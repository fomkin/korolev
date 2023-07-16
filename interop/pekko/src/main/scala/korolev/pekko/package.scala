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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.RawHeader
import org.apache.pekko.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Flow, Keep, Sink}
import org.apache.pekko.util.ByteString
import korolev.pekko.util.LoggingReporter
import korolev.data.{Bytes, BytesLike}
import korolev.effect.{Effect, Reporter, Stream}
import korolev.server.internal.BadRequestException
import korolev.server.{WebSocketRequest as KorolevWebSocketRequest, WebSocketResponse as KorolevWebSocketResponse}
import korolev.server.{KorolevService, KorolevServiceConfig, HttpRequest as KorolevHttpRequest}
import korolev.state.{StateDeserializer, StateSerializer}
import korolev.web.{PathAndQuery, Request as KorolevRequest, Response as KorolevResponse}

import scala.concurrent.{ExecutionContext, Future}

package object pekko {

  type PekkoHttpService = PekkoHttpServerConfig => Route

  import instances._

  def pekkoHttpService[F[_]: Effect, S: StateSerializer: StateDeserializer, M]
      (config: KorolevServiceConfig[F, S, M], wsLoggingEnabled: Boolean = false)
      (implicit actorSystem: ActorSystem, materializer: Materializer, ec: ExecutionContext): PekkoHttpService = { pekkoHttpConfig =>
    // If reporter wasn't overridden, use pekko-logging reporter.
    val actualConfig =
      if (config.reporter != Reporter.PrintReporter) config
      else config.copy(reporter = new LoggingReporter(actorSystem))

    val korolevServer = korolev.server.korolevService(actualConfig)
    val wsRouter = configureWsRoute(korolevServer, pekkoHttpConfig, actualConfig, wsLoggingEnabled)
    val httpRoute = configureHttpRoute(korolevServer)

    wsRouter ~ httpRoute
  }

  private def configureWsRoute[F[_]: Effect, S: StateSerializer: StateDeserializer, M]
      (korolevServer: KorolevService[F],
       pekkoHttpConfig: PekkoHttpServerConfig,
       korolevServiceConfig: KorolevServiceConfig[F, S, M],
       wsLoggingEnabled: Boolean)
      (implicit materializer: Materializer, ec: ExecutionContext): Route =
    extractRequest { request =>
      extractUnmatchedPath { path =>
        extractWebSocketUpgrade { upgrade =>
          // inSink - consume messages from the client
          // outSource - push messages to the client
          val (inStream, inSink) = Sink.korolevStream[F, Bytes].preMaterialize()
          val korolevRequest = mkKorolevRequest(request, path.toString, inStream)

          complete {
            val korolevWsRequest = KorolevWebSocketRequest(korolevRequest, upgrade.requestedProtocols)
            Effect[F].toFuture(korolevServer.ws(korolevWsRequest)).map {
              case KorolevWebSocketResponse(KorolevResponse(_, outStream, _, _), selectedProtocol) =>
                val source = outStream
                    .asPekkoSource
                    .map(text => BinaryMessage.Strict(text.as[ByteString]))
                val sink = Flow[Message]
                  .mapAsync(pekkoHttpConfig.wsStreamedParallelism) {
                    case TextMessage.Strict(message) =>
                      Future.successful(Some(BytesLike[Bytes].utf8(message)))
                    case TextMessage.Streamed(stream) =>
                      stream
                        .completionTimeout(pekkoHttpConfig.wsStreamedCompletionTimeout)
                        .runFold("")(_ + _)
                        .map(message => Some(BytesLike[Bytes].utf8(message)))
                    case BinaryMessage.Strict(data) =>
                      Future.successful(Some(Bytes.wrap(data)))
                    case BinaryMessage.Streamed(stream) =>
                      stream
                        .completionTimeout(pekkoHttpConfig.wsStreamedCompletionTimeout)
                        .runFold(ByteString.empty)(_ ++ _)
                        .map(message => Some(Bytes.wrap(message)))
                  }
                  .recover {
                    case ex =>
                      korolevServiceConfig.reporter.error(s"WebSocket exception ${ex.getMessage}, shutdown output stream", ex)
                      outStream.cancel()
                      None
                  }
                  .collect {
                    case Some(message) =>
                      message
                  }
                  .to(inSink)

                upgrade.handleMessages(
                  if(wsLoggingEnabled) {
                    Flow.fromSinkAndSourceCoupled(sink, source).log("korolev-ws")
                  } else {
                    Flow.fromSinkAndSourceCoupled(sink, source)
                  },
                  Some(selectedProtocol)
                )
              case _ =>
                throw new RuntimeException // cannot happen
            }.recover {
              case BadRequestException(message) =>
                HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(message))
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
        val bytesSource = body.asPekkoSource.map(_.as[ByteString])
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
