/*
 * Copyright 2017-2020 Aleksey Fomkin
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
import korolev.data.Bytes
import korolev.effect.{Effect, Reporter, Stream}
import korolev.server.{KorolevService, KorolevServiceConfig, HttpRequest => KorolevHttpRequest}
import korolev.state.{StateDeserializer, StateSerializer}
import korolev.web.{PathAndQuery, Request => KorolevRequest, Response => KorolevResponse}

import scala.concurrent.{ExecutionContext, Future}

package object akka {

  type AkkaHttpService = AkkaHttpServerConfig => Route

  import instances._

  def akkaHttpService[F[_]: Effect, S: StateSerializer: StateDeserializer, M]
      (config: KorolevServiceConfig[F, S, M])
      (implicit actorSystem: ActorSystem, materializer: Materializer, ec: ExecutionContext): AkkaHttpService = { akkaHttpConfig =>
    // If reporter wasn't overridden, use akka-logging reporter.
    val actualConfig =
      if (config.reporter != Reporter.PrintReporter) config
      else config.copy(reporter = new LoggingReporter(actorSystem))

    val korolevServer = korolev.server.korolevService(actualConfig)
    val wsRouter = configureWsRoute(korolevServer, akkaHttpConfig, actualConfig)
    val httpRoute = configureHttpRoute(korolevServer)

    wsRouter ~ httpRoute
  }

  private def configureWsRoute[F[_]: Effect, S: StateSerializer: StateDeserializer, M]
      (korolevServer: KorolevService[F],
       akkaHttpConfig: AkkaHttpServerConfig,
       korolevServiceConfig: KorolevServiceConfig[F, S, M])
      (implicit materializer: Materializer, ec: ExecutionContext): Route =
    extractRequest { request =>
      extractUnmatchedPath { path =>
        extractWebSocketUpgrade { upgrade =>
          // inSink - consume messages from the client
          // outSource - push messages to the client
          val (inStream, inSink) = Sink.korolevStream[F, String].preMaterialize()
          val korolevRequest = mkKorolevRequest(request, path.toString, inStream)

          complete {
            Effect[F].toFuture(korolevServer.ws(korolevRequest)).map {
              case KorolevResponse(_, outStream, _, _) =>
                val source = outStream
                    .asAkkaSource
                    .map(text => TextMessage.Strict(text))

                val sink = Flow[Message]
                    .mapConcat {
                      case tm: TextMessage.Strict => tm.text :: Nil
                      case tm: TextMessage.Streamed => tm.textStream.runWith(Sink.ignore); Nil
                      case bm: BinaryMessage => bm.dataStream.runWith(Sink.ignore); Nil
                    }
                    .to(inSink)

                upgrade.handleMessages(
                  Flow.fromSinkAndSourceCoupled(sink, source)
                )
              case _ =>
                throw new RuntimeException // cannot happen
            }
          }
        }
      }
    }

  private def configureHttpRoute[F[_]](korolevServer: KorolevService[F])(implicit mat: Materializer, async: Effect[F], ec: ExecutionContext): Route =
    extractUnmatchedPath { path =>
      extractRequest { request =>
        val sink = Sink.korolevStream[F, Bytes]
        val body =
          if (request.method == HttpMethods.GET) {
            Stream.empty[F, Bytes]
          } else {
            request
              .entity
              .dataBytes
              .map(Bytes.wrap(_))
              .toMat(sink)(Keep.right)
              .run()
          }
        val korolevRequest = mkKorolevRequest(request, path.toString, body)
        val responseF = handleHttpResponse(korolevServer, korolevRequest)
        complete(responseF)
      }
    }

  private def mkKorolevRequest[F[_], Body](request: HttpRequest,
                                     path: String,
                                     body: Body): KorolevRequest[Body] =
    KorolevRequest(
      pq = PathAndQuery.fromString(path).withParams(request.uri.rawQueryString),
      method = KorolevRequest.Method.fromString(request.method.value),
      contentLength = request.headers.find(_.is("content-length")).map(_.value().toLong),
      renderedCookie = request.headers.find(_.is("cookie")).map(_.value()).getOrElse(""),
      headers = {
        val contentType = request.entity.contentType
        val contentTypeHeaders =
          if (contentType.mediaType.isMultipart) Seq("content-type" -> contentType.toString) else Seq.empty
        request.headers.map(h => (h.name(), h.value())) ++ contentTypeHeaders
      },
      body = body
    )

  private def handleHttpResponse[F[_]: Effect](korolevServer: KorolevService[F],
                                               korolevRequest: KorolevHttpRequest[F])(implicit ec: ExecutionContext): Future[HttpResponse] =
    Effect[F].toFuture(korolevServer.http(korolevRequest)).map {
      case response @ KorolevResponse(status, body, responseHeaders, _) =>
        val (contentTypeOpt, otherHeaders) = getContentTypeAndResponseHeaders(responseHeaders)
        val bytesSource = body.asAkkaSource.map(_.as[ByteString])
        HttpResponse(
          StatusCode.int2StatusCode(status.code),
          otherHeaders,
          response.contentLength match {
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
