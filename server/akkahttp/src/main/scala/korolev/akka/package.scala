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

import _root_.akka.actor.ActorSystem
import _root_.akka.http.scaladsl.model._
import _root_.akka.http.scaladsl.model.headers.RawHeader
import _root_.akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import _root_.akka.http.scaladsl.server.Directives._
import _root_.akka.http.scaladsl.server.Route
import _root_.akka.stream.Materializer
import _root_.akka.stream.scaladsl.{Flow, Keep, Sink}
import _root_.akka.util.ByteString
import korolev.akka.util.LoggingReporter
import korolev.effect.io.LazyBytes
import korolev.effect.{Effect, Reporter}
import korolev.server.{KorolevService, KorolevServiceConfig, Request => KorolevRequest, Response => KorolevResponse}
import korolev.state.{StateDeserializer, StateSerializer}

import scala.concurrent.{ExecutionContext, Future}

package object akka {

  type AkkaHttpService = AkkaHttpServerConfig => Route

  import Converters._

  def akkaHttpService[F[_]: Effect, S: StateSerializer: StateDeserializer, M]
      (config: KorolevServiceConfig[F, S, M])
      (implicit actorSystem: ActorSystem, materializer: Materializer, ec: ExecutionContext): AkkaHttpService = { akkaHttpConfig =>
    // If reporter wasn't overridden, use akka-logging reporter.
    val actualConfig =
      if (config.reporter != Reporter.PrintReporter) config
      else config.copy(reporter = new LoggingReporter(actorSystem))

    val korolevServer = korolev.server.korolevService(actualConfig)

    webSocketRoute(korolevServer, akkaHttpConfig, actualConfig) ~
      httpGetRoute(korolevServer) ~
      httpPostRoute(korolevServer)
  }

  private def webSocketRoute[F[_]: Effect, S: StateSerializer: StateDeserializer, M]
      (korolevServer: KorolevService[F],
       akkaHttpConfig: AkkaHttpServerConfig,
       korolevServiceConfig: KorolevServiceConfig[F, S, M])
      (implicit actorSystem: ActorSystem, materializer: Materializer, ec: ExecutionContext): Route =
    extractRequest { request =>
      extractUnmatchedPath { path =>
        extractUpgradeToWebSocket { upgrade =>
          // inSink - consume messages from the client
          // outSource - push messages to the client
          val (inStream, inSink) = Sink.korolevStream[F, String].preMaterialize()
          val korolevRequest = mkKorolevRequest(request, path.toString, Map.empty, inStream)
          onSuccess(Effect[F].toFuture(korolevServer.ws(korolevRequest))) {
            case KorolevResponse(_, outStream, _) =>
              val outSource = outStream.asAkkaSource
              val response = upgrade.handleMessagesWithSinkSource(
                inSink = Flow[Message]
                  .mapConcat {
                    case tm: TextMessage.Strict => tm.text :: Nil
                    case tm: TextMessage.Streamed => tm.textStream.runWith(Sink.ignore); Nil
                    case bm: BinaryMessage => bm.dataStream.runWith(Sink.ignore); Nil
                  }
                  .to(inSink),
                outSource = outSource
                  .map(text => TextMessage.Strict(text))
              )
              complete(response)
            case _ =>
              throw new RuntimeException // cannot happen
          }
        }
      }
    }

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
      extractUnmatchedPath { path =>
        parameterMap { params =>
          extractRequest { request =>
            val sink = Sink.korolevStream[F, Array[Byte]]
            val stream = request
              .entity
              .dataBytes
              .map(_.toArray)
              .toMat(sink)(Keep.right)
              .run()
            val body = LazyBytes(stream, request.entity.contentLengthOption)
            val korolevRequest = mkKorolevRequest(request, path.toString, params, body)
            val responseF = handleHttpResponse(korolevServer, korolevRequest)
            complete(responseF)
          }
        }
      }
    }

  private def mkKorolevRequest[F[_], Body](request: HttpRequest,
                                     path: String,
                                     params: Map[String, String],
                                     body: Body): KorolevRequest[Body] =
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
                                               korolevRequest: KorolevRequest.Http[F])(implicit ec: ExecutionContext): Future[HttpResponse] =
    Effect[F].toFuture(korolevServer.http(korolevRequest)).map {
      case KorolevResponse(status, lazyBytes, responseHeaders) =>
        val (contentTypeOpt, otherHeaders) = getContentTypeAndResponseHeaders(responseHeaders)
        val bytesSource = lazyBytes.chunks.asAkkaSource.map(ByteString.apply)
        HttpResponse(
          StatusCode.int2StatusCode(status.code),
          otherHeaders,
          lazyBytes.bytesLength match {
            case Some(bytesLength) => HttpEntity(contentTypeOpt.getOrElse(ContentTypes.NoContentType), bytesLength, bytesSource)
            case None => HttpEntity(contentTypeOpt.getOrElse(ContentTypes.NoContentType), bytesSource)
          }
        )
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
