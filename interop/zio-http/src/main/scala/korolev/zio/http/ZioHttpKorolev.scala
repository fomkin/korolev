package korolev.zio.http

import _root_.zhttp.http.*
import _root_.zhttp.socket.*
import _root_.zio.stream.ZStream
import _root_.zio.{RIO, ZIO}
import io.netty.buffer.{ByteBuf, ByteBufUtil}
import korolev.data.Bytes
import korolev.effect.syntax.EffectOps
import korolev.effect.{Queue, Stream as KStream}
import korolev.server.{KorolevService, KorolevServiceConfig, HttpRequest as KorolevHttpRequest}
import korolev.state.{StateDeserializer, StateSerializer}
import korolev.web.{PathAndQuery as PQ, Request as KorolevRequest, Response as KorolevResponse}
import korolev.zio.Zio2Effect
import korolev.zio.streams.*
import zhttp.service.ChannelEvent
import zhttp.service.ChannelEvent.UserEvent.HandshakeComplete
import zhttp.service.ChannelEvent.{ChannelRead, ChannelRegistered, ChannelUnregistered, UserEventTriggered}


class ZioHttpKorolev[R] {

  type ZEffect = Zio2Effect[R, Throwable]

  def service[S: StateSerializer: StateDeserializer, M]
  (config: KorolevServiceConfig[RIO[R, *], S, M])
  (implicit eff:  ZEffect): HttpApp[R, Throwable] = {

    val korolevServer = korolev.server.korolevService(config)

    val rootPath = Path.decode(config.rootPath.mkString)

    def app(req: Request): ResponseZIO[R, Throwable] = req match {

      case req if matchWebSocket(req) =>
        routeWsRequest(req, subPath(req.url.path, rootPath.segments.length), korolevServer)

      case req =>
        routeHttpRequest(rootPath, req, korolevServer)
    }

    Http.collectZIO {
      case req if matchPrefix(rootPath, req.url.path) => app(req)
    }
  }

  private def matchWebSocket(req: Request): Boolean = {
    req.method == Method.GET && containsUpgradeHeader(req)
  }

  private def routeHttpRequest
  (rootPath: Path, req: Request, korolevServer: KorolevService[RIO[R, *]])
  (implicit eff:  ZEffect): ResponseZIO[R, Throwable] = {

    val prefLength = rootPath.segments.length

    req match {
      case req if req.method == Method.GET =>
        val body = KStream.empty[RIO[R, *], Bytes]
        val korolevRequest = mkKorolevRequest(req, subPath(req.url.path, prefLength), body)
        handleHttpResponse(korolevServer, korolevRequest)

      case req  =>
        for {
          stream <- toKorolevBody(req.data)
          korolevRequest = mkKorolevRequest(req, subPath(req.url.path, prefLength), stream)
          response <- handleHttpResponse(korolevServer, korolevRequest)
        } yield {
          response
        }
    }
  }

  private def matchPrefix(prefix: Path, url: Path): Boolean = {
    url.segments.take(prefix.segments.length) == prefix.segments
  }

  private def subPath(path: Path, prefLength: Int): String =
    path.copy(segments = path.segments.drop(prefLength)).encode

  private def containsUpgradeHeader(req: Request): Boolean = {
    val headers = req.headers.toList
    val found = for {
      _ <- headers.find { case (k, v) => k.equalsIgnoreCase("connection") && v.toLowerCase.indexOf("upgrade") > -1 }
      _ <- headers.find { case (k, v) => k.equalsIgnoreCase("upgrade") && v.toLowerCase == "websocket"  }
    } yield {}
    found.isDefined
  }

  private def routeWsRequest[S: StateSerializer: StateDeserializer, M]
  (req: Request, fullPath: String, korolevServer: KorolevService[RIO[R, *]])
  (implicit eff:  ZEffect): ResponseZIO[R, Throwable] = {


    val fromClientKQueue = Queue[RIO[R, *], String]()
    val korolevRequest = mkKorolevRequest[KStream[RIO[R, *], String]](req, fullPath, fromClientKQueue.stream)
    for {
      response <- korolevServer.ws(korolevRequest)
      toClient = response match {
        case KorolevResponse(_, outStream, _, _) =>
          outStream
            .map( out => WebSocketFrame.Text(out))
            .toZStream
        case null =>
          throw new RuntimeException
      }
      route <- buildSocket(toClient, fromClientKQueue)
    } yield {
      route
    }
  }

  private def buildSocket(
                           toClientStream: ZStream[R, Throwable, WebSocketFrame],
                           fromClientKQueue: Queue[RIO[R, *], String]
                         ): RIO[R, Response] = {

    val app = Http.collectZIO[WebSocketChannelEvent] {
      case ChannelEvent(channel,UserEventTriggered(HandshakeComplete)) =>
        toClientStream.mapZIO(frame => channel.writeAndFlush(frame)).runDrain
      case ChannelEvent(_, ChannelRead(WebSocketFrame.Text(t))) =>
        fromClientKQueue.offer(t)
      case ChannelEvent(_, ChannelRead(WebSocketFrame.Close(_, _))) =>
        fromClientKQueue.close()
      case ChannelEvent(channel, ChannelUnregistered) =>
        ZIO.unit
      case frame =>
        ZIO.fail(new Exception(s"Invalid frame type ${frame.getClass.getName}"))
    }.toSocketApp

    Response.fromSocketApp(app)
  }

  private def mkKorolevRequest[Body](request: Request,
                                     path: String,
                                     body: Body): KorolevRequest[Body] = {
    val cookies = findCookieHeader(request.headers)
    val params = request.url.queryParams.collect { case (k, v) if v.nonEmpty => (k, v.head) }
    KorolevRequest(
      pq = PQ.fromString(path).withParams(params),
      method = KorolevRequest.Method.fromString(request.method.toString()),
      renderedCookie = cookies.orNull,
      contentLength = findHeaderValue(request.headers, "content-length").map(_.toLong),
      headers = {
        val contentType = request.contentType
        val contentTypeHeaders = {
          contentType.map { ct =>
            if(String.valueOf(ct).contains("multipart")) Seq("content-type" -> contentType.toString) else Seq.empty
          }.getOrElse(Seq.empty)
        }
        request.headers.toList ++ contentTypeHeaders
      },
      body = body
    )
  }

  private def handleHttpResponse(korolevServer: KorolevService[RIO[R, *]],
                                 korolevRequest: KorolevHttpRequest[RIO[R, *]]
                                ): ResponseZIO[R, Throwable] = {
    korolevServer.http(korolevRequest).flatMap {
      case KorolevResponse(status, stream, responseHeaders, _) =>
        val headers = Headers(responseHeaders)
        val body: ZStream[R, Throwable, Byte] = stream.toZStream.flatMap { (bytes: Bytes) =>
          ZStream.fromIterable(bytes.as[Array[Byte]])
        }

        ZIO.environmentWithZIO[R](env => ZIO.attempt(HttpData.fromStream(body.provideEnvironment(env))))
          .map(data => Response(
            status = HttpStatusConverter.fromKorolevStatus(status),
            headers = headers,
            data = data
          ))
    }
  }

  private def toKorolevBody(data: HttpData)
                           (implicit eff:  ZEffect): RIO[R, KStream[RIO[R, *], Bytes]]  = {
    if(data.isEmpty) {
      ZIO.succeed(KStream.empty)
    } else {
      ZStreamOps[R, ByteBuf](data.toByteBufStream).toKorolev(eff)
        .map { kStream =>
          kStream.map(bytes => Bytes.wrap(bytes.toArray.flatMap(ByteBufUtil.getBytes(_))))
        }
    }
  }

  private def findCookieHeader(headers: Headers): Option[String] = {
    findHeaderValue(headers, "cookie")
  }

  private def findHeaderValue(headers: Headers,
                              name: String
                             ): Option[String] =
    headers.toList
      .collectFirst { case (k, v) if k.toLowerCase() == name => v }

}
