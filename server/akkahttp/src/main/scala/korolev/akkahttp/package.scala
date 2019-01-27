/*
 * Copyright 2017-2018 Aleksey Fomkin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package korolev

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import korolev.akkahttp.util.{IncomingMessageHandler, LoggingReporter}
import korolev.execution.defaultExecutor
import korolev.server.{KorolevService, KorolevServiceConfig, MimeTypes, Request => KorolevRequest, Response => KorolevResponse}
import korolev.state.{StateDeserializer, StateSerializer}

import scala.concurrent.{Future, Promise}
import scala.util.Success

package object akkahttp {

  type AkkaHttpService = AkkaHttpServerConfig => Route

  def akkaHttpService[F[_]: Async, S: StateSerializer: StateDeserializer, M]
      (config: KorolevServiceConfig[F, S, M], mimeTypes: MimeTypes = server.mimeTypes)
      (implicit actorSystem: ActorSystem, materializer: Materializer): AkkaHttpService = { akkaHttpConfig =>
    // If reporter wasn't overridden, use akka-logging reporter.
    val actualConfig =
      if (config.reporter != Reporter.PrintReporter) config
      else config.copy(reporter = new LoggingReporter(actorSystem))

    val korolevServer = korolev.server.korolevService(mimeTypes, actualConfig)

    webSocketRoute(korolevServer, akkaHttpConfig, actualConfig) ~
      httpGetRoute(korolevServer) ~
      httpPostRoute(korolevServer)
  }

  private val KeepAliveMessage = TextMessage("[9]")

  private def webSocketRoute[F[_]: Async, S: StateSerializer: StateDeserializer, M]
      (korolevServer: KorolevService[F],
       akkaHttpConfig: AkkaHttpServerConfig,
       korolevServiceConfig: KorolevServiceConfig[F, S, M])
      (implicit actorSystem: ActorSystem, materializer: Materializer): Route =
    extractRequest { request =>
      extractUnmatchedPath { path =>
        extractUpgradeToWebSocket { upgrade =>
          val korolevRequest = mkKorolevRequest(request, path.toString, Map.empty, LazyBytes.empty)

          onSuccess(asyncToFuture(korolevServer(korolevRequest))) {
            case KorolevResponse.WebSocket(publish, subscribe, destroy) =>
              val messageHandler = new IncomingMessageHandler(publish, () => destroy())
              val in = inFlow(akkaHttpConfig.maxRequestBodySize, publish).to(messageHandler.asSink)

              val out =
                Source
                  .actorRef[TextMessage](akkaHttpConfig.outputBufferSize, OverflowStrategy.fail)
                  .mapMaterializedValue[Unit] { actorRef =>
                    subscribe { message =>
                      actorRef ! TextMessage(message)
                    }
                  }
                  .keepAlive(korolevServiceConfig.heartbeatInterval, () => KeepAliveMessage)

              complete(upgrade.handleMessagesWithSinkSource(in, out))
            case _ =>
              throw new RuntimeException // cannot happen
          }
        }
      }
    }

  private def inFlow(maxMessageSize: Int, publish: String => Unit)
                    (implicit materializer: Materializer): Flow[Message, String, NotUsed] =
    Flow[Message]
      .mapAsync(1) {
        case TextMessage.Strict(text) =>
          Future.successful(Some(text))
        case TextMessage.Streamed(stream) =>
          stream.take(maxMessageSize.toLong)
            .runFold(new java.lang.StringBuilder)((b, s) => { b.append(s); b })
            .map(b => Some(b.toString))
        case bm: BinaryMessage =>
          bm.dataStream.runWith(Sink.ignore).map(_ => None)
      }
      .collect { case Some(body) => body }

  private def httpGetRoute[F[_]: Async](korolevServer: KorolevService[F]): Route =
    get {
      extractRequest { request =>
        extractUnmatchedPath { path =>
          parameterMap { params =>
            val korolevRequest = mkKorolevRequest(request, path.toString, params, LazyBytes.empty)
            val responseF = handleHttpResponse(korolevServer, korolevRequest)
            complete(responseF)
          }
        }
      }
    }

  private def httpPostRoute[F[_]](korolevServer: KorolevService[F])(implicit mat: Materializer, async: Async[F]): Route =
    post {
      extractRequest { request =>
        extractUnmatchedPath { path =>
          parameterMap { params =>
            extractRequest { request =>
              val queue = request
                .entity
                .dataBytes
                .map(_.toArray)
                .toMat(Sink.queue[Array[Byte]])(Keep.right)
                .run()
              val finished = async.promise[Unit]
              val pull = { () =>
                val future = queue.pull()
                val promise = async.promise[Option[Array[Byte]]]
                future.onComplete { `try` =>
                  if (`try`.isSuccess && `try`.get.isEmpty)
                    finished.complete(Success(()))
                  promise.complete(`try`)
                }
                promise.async
              }
              val body = LazyBytes(
                pull = pull,
                cancel = () => async.pure(queue.cancel()),
                finished = finished.async,
                size = request.entity.contentLengthOption
              )
              val korolevRequest = mkKorolevRequest(request, path.toString, params, body)
              val responseF = handleHttpResponse(korolevServer, korolevRequest)
              complete(responseF)
            }
          }
        }
      }
    }

  private def mkKorolevRequest[F[_]](request: HttpRequest,
                                            path: String,
                                            params: Map[String, String],
                                            body: LazyBytes[F])
                                    (implicit async: Async[F]): KorolevRequest[F] =
    KorolevRequest(
      path = Router.Path.fromString(path),
      params,
      cookie = key => request.cookies.find(_.name == key).map(_.value),
      headers = {
        val contentType = request.entity.contentType
        val contentTypeHeaders =
          if (contentType.mediaType.isMultipart) Seq("content-type" -> contentType.toString) else Seq.empty

        request.headers.map(h => (h.name(), h.value())) ++ contentTypeHeaders
      },
      body = body
    )

  private def handleHttpResponse[F[_]: Async](korolevServer: KorolevService[F],
                                               korolevRequest: KorolevRequest[F]): Future[HttpResponse] =
    asyncToFuture(korolevServer(korolevRequest)).map {
      case KorolevResponse.Http(status, streamOpt, responseHeaders) =>
        val (contentTypeOpt, otherHeaders) = getContentTypeAndResponseHeaders(responseHeaders)
        val array = streamOpt.getOrElse(Array.empty)

        HttpResponse(
          StatusCode.int2StatusCode(status.code),
          otherHeaders,
          HttpEntity(contentTypeOpt.getOrElse(ContentTypes.NoContentType), array)
        )
      case _ =>
        throw new RuntimeException // cannot happen
    }

  private def getContentTypeAndResponseHeaders(responseHeaders: Seq[(String, String)]): (Option[ContentType], List[HttpHeader]) = {
    val headers = responseHeaders.map { case (name, value) =>
      HttpHeader.parse(name, value) match {
        case HttpHeader.ParsingResult.Ok(header, _) => header
        case _ => RawHeader(name, value)
      }
    }
    val (contentTypeHeaders, otherHeaders) = headers.partition(_.lowercaseName() == "content-type")
    val contentTypeOpt = contentTypeHeaders.headOption.flatMap(h => ContentType.parse(h.value()).right.toOption)

    (contentTypeOpt, otherHeaders.toList)
  }

  private def asyncToFuture[F[_]: Async, T](f: F[T]): Future[T] =
    if (f.isInstanceOf[Future[_]]: @unchecked) f.asInstanceOf[Future[T]]
    else {
      val p = Promise[T]()
      Async[F].run(f)(p.complete)
      p.future
    }
}
