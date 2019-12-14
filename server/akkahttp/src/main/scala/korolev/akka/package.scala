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

import korolev.effect.Effect
import korolev.effect.Reporter
import korolev.effect.Stream

import _root_.akka.NotUsed
import _root_.akka.actor.ActorSystem
import _root_.akka.http.scaladsl.model._
import _root_.akka.http.scaladsl.model.headers.RawHeader
import _root_.akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import _root_.akka.http.scaladsl.server.Directives._
import _root_.akka.http.scaladsl.server.Route
import _root_.akka.stream.scaladsl.{Flow, Sink, Source}
import _root_.akka.stream.{Materializer, OverflowStrategy}
import _root_.akka.util.ByteString

import korolev.akka.util.{IncomingMessageHandler, LoggingReporter}
import korolev.server.{KorolevService, KorolevServiceConfig, MimeTypes, Request => KorolevRequest, Response => KorolevResponse}
import korolev.state.{StateDeserializer, StateSerializer}
import org.reactivestreams.{Subscriber, Subscription}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

package object akka {

  type AkkaHttpService = AkkaHttpServerConfig => Route

  def akkaHttpService[F[_]: Effect, S: StateSerializer: StateDeserializer, M]
      (config: KorolevServiceConfig[F, S, M], mimeTypes: MimeTypes = server.mimeTypes)
      (implicit actorSystem: ActorSystem, materializer: Materializer, ec: ExecutionContext): AkkaHttpService = { akkaHttpConfig =>
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

  private def webSocketRoute[F[_]: Effect, S: StateSerializer: StateDeserializer, M]
      (korolevServer: KorolevService[F],
       akkaHttpConfig: AkkaHttpServerConfig,
       korolevServiceConfig: KorolevServiceConfig[F, S, M])
      (implicit actorSystem: ActorSystem, materializer: Materializer, ec: ExecutionContext): Route =
    extractRequest { request =>
      extractUnmatchedPath { path =>
        extractUpgradeToWebSocket { upgrade =>
          val korolevRequest = mkKorolevRequest(request, path.toString, Map.empty, LazyBytes.empty)

          onSuccess(Effect[F].toFuture(korolevServer(korolevRequest))) {
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
                    (implicit materializer: Materializer, ec: ExecutionContext): Flow[Message, String, NotUsed] =
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

  private def httpGetRoute[F[_]: Effect](korolevServer: KorolevService[F])(implicit ec: ExecutionContext): Route =
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

  private def httpPostRoute[F[_]](korolevServer: KorolevService[F])(implicit mat: Materializer, async: Effect[F], ec: ExecutionContext): Route =
    post {
      extractRequest { _ =>
        extractUnmatchedPath { path =>
          parameterMap { params =>
            extractRequest { request =>

              val subscriber = new Subscriber[Array[Byte]] {

                var subscription: Subscription = _
                var promise: Effect.Promise[F, Option[Array[Byte]]] = _
                val completed: Boolean = false

                def onSubscribe(s: Subscription): Unit = {
                  subscription = s
                  if (promise != null)
                    subscription.request(1)
                }

                def onNext(t: Array[Byte]): Unit =
                  promise.complete(Success(Some(t)))

                def onError(t: Throwable): Unit =
                  promise.complete(Failure(t))

                def onComplete(): Unit = {
                  if (promise != null)
                    promise.complete(Success(None))
                  finished.complete(Success(()))
                }

                val finished: Effect.Promise[F, Unit] = Effect[F].promise[Unit]

                def pull(): F[Option[Array[Byte]]] = {
                  if (subscription != null)
                    subscription.request(1)
                  promise = Effect[F].promise[Option[Array[Byte]]]
                  promise.effect
                }

                def cancel(): F[Unit] = {
                  Effect[F].delay(subscription.cancel())
                }
              }

              request
                .entity
                .dataBytes
                .map(_.toArray)
                .to(Sink.fromSubscriber(subscriber))
                .run()

              val chunks = Stream[F, Array[Byte]](
                pull = subscriber.pull,
                cancel = subscriber.cancel,
                consumed = subscriber.finished.effect,
                size = None
              )
              val body = LazyBytes(chunks,  request.entity.contentLengthOption)
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
                                     body: LazyBytes[F]): KorolevRequest[F] =
    KorolevRequest(
      path = Router.Path.fromString(path),
      param = params.get,
      cookie = key => request.cookies.find(_.name == key).map(_.value),
      headers = {
        val contentType = request.entity.contentType
        val contentTypeHeaders =
          if (contentType.mediaType.isMultipart) Seq("content-type" -> contentType.toString) else Seq.empty
        request.headers.map(h => (h.name(), h.value())) ++ contentTypeHeaders
      },
      body = body
    )

  private def handleHttpResponse[F[_]: Effect](korolevServer: KorolevService[F],
                                               korolevRequest: KorolevRequest[F])(implicit ec: ExecutionContext): Future[HttpResponse] =
    Effect[F].toFuture(korolevServer(korolevRequest)).map {
      case KorolevResponse.Http(status, lazyBytes, responseHeaders) =>
        val (contentTypeOpt, otherHeaders) = getContentTypeAndResponseHeaders(responseHeaders)
        val bytesSource = Source
          .unfoldAsync[NotUsed, Array[Byte]](NotUsed) { _ =>
            Effect[F].toFuture(
              Effect[F].map(lazyBytes.chunks.pull()) { vOpt =>
                vOpt.map(v => (NotUsed, v))
              }
            )
          }
          .map(ByteString.apply)
        HttpResponse(
          StatusCode.int2StatusCode(status.code),
          otherHeaders,
          lazyBytes.bytesLength match {
            case Some(bytesLength) => HttpEntity(contentTypeOpt.getOrElse(ContentTypes.NoContentType), bytesLength, bytesSource)
            case None => HttpEntity(contentTypeOpt.getOrElse(ContentTypes.NoContentType), bytesSource)
          }
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
}
