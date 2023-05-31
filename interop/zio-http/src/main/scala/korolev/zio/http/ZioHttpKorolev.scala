package korolev.zio.http

import _root_.zio.http.*
import _root_.zio.stream.ZStream
import _root_.zio.{RIO, ZIO}
import korolev.data.Bytes
import korolev.effect.{Queue, Stream as KStream}
import korolev.server.{KorolevService, KorolevServiceConfig, HttpRequest as KorolevHttpRequest}
import korolev.state.{StateDeserializer, StateSerializer}
import korolev.web.{PathAndQuery as PQ, Request as KorolevRequest, Response as KorolevResponse}
import korolev.zio.Zio2Effect
import korolev.zio.streams.*


class ZioHttpKorolev[R] {

  type ZEffect = Zio2Effect[R, Throwable]

  def service[S: StateSerializer: StateDeserializer, M]
  (config: KorolevServiceConfig[RIO[R, *], S, M])
  (implicit eff:  ZEffect): HttpApp[R, Throwable] = {

    val korolevServer = korolev.server.korolevService(config)

    val rootPath = Path.decode(config.rootPath.mkString)

    def app(req: Request): ZIO[R, Throwable, Response] = req match {

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
  (implicit eff:  ZEffect): ZIO[R, Throwable, Response] = {

    val prefLength = rootPath.segments.length

    req match {
      case req if req.method == Method.GET =>
        val body = KStream.empty[RIO[R, *], Bytes]
        val korolevRequest = mkKorolevRequest(req, subPath(req.url.path, prefLength), body)
        handleHttpResponse(korolevServer, korolevRequest)

      case req  =>
        for {
          stream <- toKorolevBody(req.body)
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
    val found = for {
      _ <- req.rawHeader(Header.Connection).filter(_.toLowerCase.indexOf("upgrade") > -1)
      _ <- req.rawHeader(Header.Upgrade).filter(_.toLowerCase.indexOf("websocket") > -1)
    } yield {}
    found.isDefined
  }

  private def routeWsRequest[S: StateSerializer: StateDeserializer, M]
  (req: Request, fullPath: String, korolevServer: KorolevService[RIO[R, *]])
  (implicit eff:  ZEffect): ZIO[R, Throwable, Response] = {


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
    val socket = Handler.webSocket { channel =>
      channel.receiveAll {
        case ChannelEvent.UserEventTriggered(ChannelEvent.UserEvent.HandshakeComplete) => {
          toClientStream.mapZIO(frame => channel.send(ChannelEvent.Read(frame))).runDrain.forkDaemon
        }
          
        case ChannelEvent.Read(WebSocketFrame.Text(t)) => 
          fromClientKQueue.offer(t)
        case ChannelEvent.Read(WebSocketFrame.Close(_, _)) => 
          fromClientKQueue.close()
        case ChannelEvent.Unregistered =>
          ZIO.unit
        case frame => 
          ZIO.fail(new Exception(s"Invalid frame type ${frame.getClass.getName}"))
        }
    }

    Response.fromSocketApp(socket)
  }

  private def mkKorolevRequest[B](request: Request,
                                     path: String,
                                     body: B): KorolevRequest[B] = {
    val cookies = request.rawHeader(Header.Cookie)
    val params = request.url.queryParams.map.collect { case (k, v) if v.nonEmpty => (k, v.head) }
    KorolevRequest(
      pq = PQ.fromString(path).withParams(params),
      method = KorolevRequest.Method.fromString(request.method.name),
      renderedCookie = cookies.orNull,
      contentLength = request.header(Header.ContentLength).map(_.length),
      headers = {
        val contentType = request.header(Header.ContentType)
        val contentTypeHeader = {
          contentType.map { ct =>
            if(ct.renderedValue.contains("multipart")) Headers(ct) else Headers.empty
          }.getOrElse(Headers.empty)
        }
        (request.headers.toList ++ contentTypeHeader).map(header => header.headerName -> header.renderedValue)
      },
      body = body
    )
  }

  private def handleHttpResponse(korolevServer: KorolevService[RIO[R, *]],
                                 korolevRequest: KorolevHttpRequest[RIO[R, *]]
                                ): ZIO[R, Throwable, Response] = {
    korolevServer.http(korolevRequest).flatMap {
      case KorolevResponse(status, stream, responseHeaders, _) =>
        val headers = Headers(responseHeaders.map { case (name, value) => Header.Custom(name, value)})
        val body: ZStream[R, Throwable, Byte] = stream.toZStream.flatMap { (bytes: Bytes) =>
          ZStream.fromIterable(bytes.as[Array[Byte]])
        }

        ZIO.environmentWithZIO[R](env => ZIO.attempt(Body.fromStream(body.provideEnvironment(env))))
          .map(body => Response(
            status = HttpStatusConverter.fromKorolevStatus(status),
            headers = headers,
            body = body
          ))
    }
  }

  private def toKorolevBody(body: Body)
                           (implicit eff:  ZEffect): RIO[R, KStream[RIO[R, *], Bytes]]  = {
    ZStreamOps[R, Byte](body.asStream).toKorolev(eff)
      .map { kStream =>
        kStream.map(bytes => Bytes.wrap(bytes.toArray))
      }
  }

}
