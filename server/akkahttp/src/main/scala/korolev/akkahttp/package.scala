package korolev

import java.nio.ByteBuffer

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink}
import korolev.akkahttp.util.{IncomingMessageHandler, OutgoingMessageWriter}
import korolev.execution.defaultExecutor
import korolev.server.{KorolevService, KorolevServiceConfig, MimeTypes, Request => KorolevRequest, Response => KorolevResponse}
import korolev.state.{StateDeserializer, StateSerializer}

import scala.concurrent.Future

package object akkahttp {

  type AkkaHttpService = AkkaHttpServerConfig => Route

  def akkaHttpService[S: StateSerializer: StateDeserializer, M]
      (config: KorolevServiceConfig[Future, S, M], mimeTypes: MimeTypes = server.mimeTypes)
      (implicit actorSystem: ActorSystem, materializer: Materializer): AkkaHttpService = { akkaHttpConfig =>
    val korolevServer = korolev.server.korolevService(mimeTypes, config)

    webSocketRoute(korolevServer, akkaHttpConfig) ~
      httpGetRoute(korolevServer) ~
      httpPostRoute(korolevServer)
  }

  private def webSocketRoute(korolevServer: KorolevService[Future],
                             akkaHttpConfig: AkkaHttpServerConfig)
                            (implicit actorSystem: ActorSystem,
                                      materializer: Materializer): Route =
    extractRequest { request =>
      extractUpgradeToWebSocket { upgrade =>
        val korolevRequest = mkKorolevRequest(request)

        onSuccess(korolevServer(korolevRequest)) {
          case KorolevResponse.WebSocket(publish, subscribe, destroy) =>
            val messageWriter = new OutgoingMessageWriter()
            val out = messageWriter.asSource
            subscribe(messageWriter.write)

            val messageHandler = new IncomingMessageHandler(
              publish,
              () => {
                messageWriter.close()
                destroy()
              }
            )
            val in = inFlow(akkaHttpConfig.maxRequestBodySize, publish).to(messageHandler.asSink)

            complete(upgrade.handleMessagesWithSinkSource(in, out))
          case _ =>
              throw new RuntimeException // cannot happen
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

  private def httpGetRoute(korolevServer: KorolevService[Future]): Route =
    get {
      extractRequest { request =>
        parameterMap { params =>
          val korolevRequest = mkKorolevRequest(request, params)
          val responseF = handleHttpResponse(korolevServer, korolevRequest)
          complete(responseF)
        }
      }
    }

  private def httpPostRoute(korolevServer: KorolevService[Future]): Route =
    post {
      extractRequest { request =>
        parameterMap { params =>
          entity(as[Array[Byte]]) { body =>
            val korolevRequest = mkKorolevRequest(request, params, Some(body))
            val responseF = handleHttpResponse(korolevServer, korolevRequest)
            complete(responseF)
          }
        }
      }
    }

  private def mkKorolevRequest(request: HttpRequest,
                               params: Map[String, String] = Map.empty,
                               body: Option[Array[Byte]] = None): KorolevRequest =
    KorolevRequest(
      path = Router.Path.fromString(request.uri.path.toString()),
      params,
      cookie = key => request.cookies.find(_.name == key).map(_.value),
      headers = {
        val contentType = request.entity.contentType
        val contentTypeHeaderSeq =
          if (contentType.mediaType.isMultipart) Seq("content-type" -> contentType.toString) else Seq.empty

        request.headers.map(h => (h.name(), h.value())) ++ contentTypeHeaderSeq
      },
      body = body.fold(ByteBuffer.allocate(0))(ByteBuffer.wrap)
    )

  private def handleHttpResponse(korolevServer: KorolevService[Future],
                                 korolevRequest: KorolevRequest): Future[HttpResponse] =
    korolevServer(korolevRequest).map {
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
    val contentTypeOpt =
      contentTypeHeaders.headOption.flatMap(h => ContentType.parse(h.value()).right.toOption)

    (contentTypeOpt, otherHeaders.toList)
  }

}
