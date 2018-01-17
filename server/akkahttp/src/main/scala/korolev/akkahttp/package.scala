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
import korolev.akkahttp.util.{IncomingMessageHandler, WSSubscriptionStage}
import korolev.execution.defaultExecutor
import korolev.server.{KorolevService, KorolevServiceConfig, MimeTypes, Request => KorolevRequest, Response => KorolevResponse}
import korolev.state.{StateDeserializer, StateSerializer}

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._

package object akkahttp {

  type AkkaHttpService = AkkaHttpServerConfig => Route

  def akkaHttpService[F[+_]: Async, S: StateSerializer: StateDeserializer, M]
      (config: KorolevServiceConfig[F, S, M], mimeTypes: MimeTypes = server.mimeTypes)
      (implicit actorSystem: ActorSystem, materializer: Materializer): AkkaHttpService = { akkaHttpConfig =>
    val korolevServer = korolev.server.korolevService(mimeTypes, config)

    webSocketRoute(korolevServer, akkaHttpConfig) ~
      httpGetRoute(korolevServer) ~
      httpPostRoute(korolevServer)
  }

  private val KeepAliveInterval = 5.seconds
  private val KeepAliveMessage = TextMessage("[9]")

  private def webSocketRoute[F[+_]: Async](korolevServer: KorolevService[F],
                                           akkaHttpConfig: AkkaHttpServerConfig)
                                          (implicit actorSystem: ActorSystem,
                                                    materializer: Materializer): Route =
    extractRequest { request =>
      extractUpgradeToWebSocket { upgrade =>
        val korolevRequest = mkKorolevRequest(request)

        onSuccess(asyncToFuture(korolevServer(korolevRequest))) {
          case KorolevResponse.WebSocket(publish, subscribe, destroy) =>
            val messageHandler = new IncomingMessageHandler(publish, () => destroy())
            val in = inFlow(akkaHttpConfig.maxRequestBodySize, publish).to(messageHandler.asSink)
            val out = WSSubscriptionStage.source(subscribe).keepAlive(KeepAliveInterval, () => KeepAliveMessage)

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

  private def httpGetRoute[F[+_]: Async](korolevServer: KorolevService[F]): Route =
    get {
      extractRequest { request =>
        parameterMap { params =>
          val korolevRequest = mkKorolevRequest(request, params)
          val responseF = handleHttpResponse(korolevServer, korolevRequest)
          complete(responseF)
        }
      }
    }

  private def httpPostRoute[F[+_]: Async](korolevServer: KorolevService[F]): Route =
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

  private def handleHttpResponse[F[+_]: Async](korolevServer: KorolevService[F],
                                               korolevRequest: KorolevRequest): Future[HttpResponse] =
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

  private def asyncToFuture[F[+_]: Async, T](f: F[T]): Future[T] = {
    val p = Promise[T]()
    Async[F].run(f)(p.complete)
    p.future
  }

}
