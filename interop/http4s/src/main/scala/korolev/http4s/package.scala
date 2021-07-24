package korolev

import korolev.effect.{Effect, Queue, Stream => KStream}
import korolev.server.{KorolevService, KorolevServiceConfig, HttpRequest => KorolevHttpRequest}
import korolev.web.{PathAndQuery => PQ, Request => KorolevRequest, Response => KorolevResponse}
import korolev.state.{StateDeserializer, StateSerializer}
import korolev.fs2._
import korolev.scodec._
import korolev.data.Bytes
import org.http4s.{Header, Headers, HttpRoutes, Request, Response, Status}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{Cookie, `Content-Length`}
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame.{Close, Text}
import org.http4s.websocket.WebSocketFrame

import scala.concurrent.ExecutionContext
import _root_.cats.effect.ConcurrentEffect
import _root_.cats.syntax.all._
import _root_.fs2.{Pipe, Stream => FS2Stream}
import _root_.fs2.Chunk
import _root_.scodec.bits.ByteVector
import org.http4s.dsl.impl.Path

package object http4s {

  object CloseCode {
    val NormalClosure = 1000
  }

  def http4sKorolevService[F[_]: Effect: ConcurrentEffect, S: StateSerializer: StateDeserializer, M]
      (config: KorolevServiceConfig[F, S, M])
      (implicit ec: ExecutionContext): HttpRoutes[F] = {

    val dsl: Http4sDsl[F] = Http4sDsl[F]
    import dsl._

    val korolevServer = korolev.server.korolevService(config)

    HttpRoutes.of[F] {
      case wsReq @ GET -> path if containsUpgradeHeader(wsReq) =>
        routeWsRequest(wsReq, path, korolevServer)

      case otherReqs =>
        routeHttpRequest(otherReqs, korolevServer)
    }
  }

  private def containsUpgradeHeader[F[_]: Effect: ConcurrentEffect](req: Request[F]): Boolean = {
    val headers = req.headers.toList
    val found = for {
      _ <- headers.find(h => h.name.value.toLowerCase == "connection" && h.value.toLowerCase == "upgrade")
      _ <- headers.find(h => h.name.value.toLowerCase == "upgrade" && h.value.toLowerCase == "websocket")
    } yield {}
    found.isDefined
  }

  private def routeWsRequest[F[_]: Effect: ConcurrentEffect, S: StateSerializer: StateDeserializer, M]
  (req: Request[F], path: Path, korolevServer: KorolevService[F])
  (implicit ec: ExecutionContext): F[Response[F]] = {

    val dsl: Http4sDsl[F] = Http4sDsl[F]
    import dsl._

    val (fromClient, kStream) = makeSinkAndSubscriber()

    val fullPath = path.toString
    val korolevRequest = mkKorolevRequest[F, KStream[F, String]](req, fullPath, kStream)

    for {
      response <- korolevServer.ws(korolevRequest)
      toClient = response match {
          case KorolevResponse(_, outStream, _, _) =>
            outStream
              .map(out => Text(out))
              .toFs2
              .onComplete(
                FS2Stream(Close(CloseCode.NormalClosure)).map(_.right.get)
              )
          case null =>
            throw new RuntimeException
        }
      route <- WebSocketBuilder[F].build(toClient, fromClient)
    } yield {
      route
    }
  }

  private def makeSinkAndSubscriber[F[_]: Effect: ConcurrentEffect]() = {
    val queue = Queue[F, String]()
    val sink: Pipe[F, WebSocketFrame, Unit]  = (in: FS2Stream[F, WebSocketFrame]) => {
      in.evalMap {
        case Text(t, _) if t != null =>
          queue.enqueue(t)
        case _: Close =>
          ConcurrentEffect[F].unit
        case f =>
          throw new Exception(s"Invalid frame type ${f.getClass.getName}")
      }.onFinalizeCase(_ => queue.close())
    }
    (sink, queue.stream)
  }

  private def routeHttpRequest[F[_]: Effect: ConcurrentEffect]
    (request: Request[F], korolevServer: KorolevService[F])
    (implicit ec: ExecutionContext): F[Response[F]] = {

    val dsl: Http4sDsl[F] = Http4sDsl[F]
    import dsl._

    request match {
      case req @ GET -> path =>
        val body = KStream.empty[F, Bytes]
        val korolevRequest = mkKorolevRequest(req, path.toString, body)
        handleHttpResponse(korolevServer, korolevRequest)

      case req =>
        for {
          stream <- req.body.chunks.map(ch => Bytes.wrap(ch.toByteVector)).toKorolev()
          korolevRequest = mkKorolevRequest(req, req.uri.toString(), stream)
          response <- handleHttpResponse(korolevServer, korolevRequest)
        } yield {
          response
        }
    }
  }

  private def handleHttpResponse[F[_]: Effect: ConcurrentEffect](korolevServer: KorolevService[F],
                                                                  korolevRequest: KorolevHttpRequest[F])
                                                                (implicit ec: ExecutionContext)= {
    korolevServer.http(korolevRequest).map {
      case KorolevResponse(status, stream, responseHeaders, _) =>
        val headers = getContentTypeAndResponseHeaders(responseHeaders)
        val body = stream.toFs2.flatMap { bytes =>
          val bv = bytes.as[ByteVector]
          val chunk = Chunk.byteVector(bv)
          FS2Stream.chunk(chunk)
        }
        Response[F](
          status = Status(status.code),
          headers = Headers.of(headers: _*),
          body = body
        )
    }

  }

  private def getContentTypeAndResponseHeaders(responseHeaders: Seq[(String, String)]) = {
    responseHeaders.map { case (name, value) => Header(name, value) }
  }

  private def mkKorolevRequest[F[_], Body](request: Request[F],
                                           path: String,
                                           body: Body): KorolevRequest[Body] = {
    val cookies = request.headers.get(Cookie).map(x => x.value)
    KorolevRequest(
      pq = PQ.fromString(path).withParams(request.params),
      method = KorolevRequest.Method.fromString(request.method.name),
      renderedCookie = cookies.orNull,
      contentLength = request.headers.find(_.name == `Content-Length`.name).map(_.value.toLong),
      headers = {
        val contentType = request.contentType
        val contentTypeHeaders = {
          contentType.map { ct =>
            if(ct.mediaType.isMultipart) Seq("content-type" -> contentType.toString) else Seq.empty
          }.getOrElse(Seq.empty)
        }
        request.headers.toList.map(h => (h.name.value, h.value)) ++ contentTypeHeaders
      },
      body = body
    )
  }

}
