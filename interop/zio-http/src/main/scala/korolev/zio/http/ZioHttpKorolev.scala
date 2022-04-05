package korolev.zio.http

import _root_.zhttp.http._
import _root_.zhttp.socket._
import _root_.zio.stream.ZStream
import _root_.zio.{RIO, Task, ZIO}
import korolev.data.Bytes
import korolev.effect.{Queue, Stream => KStream}
import korolev.server.{KorolevService, KorolevServiceConfig, HttpRequest => KorolevHttpRequest}
import korolev.state.{StateDeserializer, StateSerializer}
import korolev.web.{PathAndQuery => PQ, Request => KorolevRequest, Response => KorolevResponse}
import korolev.zio.ZioEffect
import korolev.zio.streams._


class ZioHttpKorolev[R] {

  type ZEffect = ZioEffect[R, Throwable]

  def service[S: StateSerializer: StateDeserializer, M]
  (config: KorolevServiceConfig[RIO[R, *], S, M])
  (implicit eff:  ZEffect): HttpApp[R, Throwable] = {

    val korolevServer = korolev.server.korolevService(config)

    val rootPath = Path(config.rootPath)

    def app(req: Request): ResponseZIO[R, Throwable] = req match {

      case req if matchWebSocket(req) =>
        routeWsRequest(req, subPath(req.url.path, rootPath.toList.length), korolevServer)

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

    val prefLength = rootPath.toList.length

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
    url.toList.take(prefix.toList.length) == prefix.toList
  }

  private def subPath(path: Path, prefLength: Int): String = {
    Path(path.toList.drop(prefLength)).toString()
  }

  private def containsUpgradeHeader(req: Request): Boolean = {
    val headers = req.headers.toList
    val found = for {
      _ <- headers.find{case (h, v) => h.toLowerCase == "connection" && v.toLowerCase == "upgrade"}
      _ <- headers.find{ case (h, v) => h.toLowerCase == "upgrade" && v.toLowerCase == "websocket"}
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
            .map(out => WebSocketFrame.Text(out))
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


    val onMessage: Socket[R, Throwable, WebSocketFrame, WebSocketFrame] = Socket
      .fromFunction[WebSocketFrame] { _ => ZStream.empty }
      .contramapZIO {
        case f @ WebSocketFrame.Text(t) =>
          fromClientKQueue.offer(t).as(f)
        case f: WebSocketFrame.Close =>
          fromClientKQueue.close().as(f)
        case frame =>
          ZIO.fail(new Exception(s"Invalid frame type ${frame.getClass.getName}"))
      }

    val app = {
      SocketApp()
        .onOpen(Socket.fromFunction[Any] { _ => toClientStream})
        .onMessage(onMessage)
        .onClose(_ => fromClientKQueue.close().asInstanceOf[ZIO[R, Nothing, Any]])
        .withDecoder(SocketDecoder.allowExtensions)
    }


    Response.fromSocketApp(app)
  }

  private def mkKorolevRequest[Body](request: Request,
                                     path: String,
                                     body: Body): KorolevRequest[Body] = {
    val cookies = findCookieHeader(request.headers.toList)
    val params = request.url.queryParams.collect { case (k, v) if v.nonEmpty => (k, v.head) }
    KorolevRequest(
      pq = PQ.fromString(path).withParams(params),
      method = KorolevRequest.Method.fromString(request.method.toString()),
      renderedCookie = cookies.orNull,
      contentLength = findHeaderValue(request.headers.toList, "content-length").map(_.toLong),
      headers = {
        val contentType = request.contentType.map(_.toString)
        val contentTypeHeaders = {
          contentType.map { ct =>
            if(ct.contains("multipart")) Seq("content-type" -> contentType.toString) else Seq.empty
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
        val body = stream.toZStream.flatMap { (bytes: Bytes) =>
          ZStream.fromIterable(bytes.as[Array[Byte]])
        }

        for {
          env <- ZIO.environment[R]
          data = HttpData.fromStream(body.provide(env))
        } yield Response(
          status = HttpStatusConverter.fromKorolevStatus(status),
          headers = Headers(responseHeaders),
          data = data
        )
    }

  }

  private def toKorolevBody(data: HttpData)
                           (implicit eff:  ZEffect): RIO[R, KStream[RIO[R, *], Bytes]]  = {

    if (data.isEmpty) {
      Task(KStream.empty)
    } else {
      data.toByteBufStream.toKorolev(eff).map(kStream =>
        kStream.map(bytes => Bytes.wrap(bytes.toArray.flatMap(_.array())))
      ).useNow
    }
  }

  private def findCookieHeader(headers: List[Header]): Option[String] = {
    findHeaderValue(headers, "cookie")
  }

  private def findHeaderValue(headers: List[Header],
                              name: String
                             ): Option[String] = {
    headers
      .collectFirst { case (n, value) if n.toString.toLowerCase == name => value.toString }
  }

}
