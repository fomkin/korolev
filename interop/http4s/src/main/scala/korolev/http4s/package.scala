package korolev

import korolev.effect.{Effect, Queue, Stream as KStream}
import korolev.server.{KorolevService, KorolevServiceConfig, HttpRequest as KorolevHttpRequest}
import korolev.web.{PathAndQuery as PQ, Request as KorolevRequest, Response as KorolevResponse}
import korolev.state.{StateDeserializer, StateSerializer}
import korolev.fs2._
import korolev.scodec._
import korolev.data.Bytes
import org.http4s.{Header, Headers, HttpRoutes, Request, Response, Status}
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{Cookie, `Content-Length`}
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame.{Close, Continuation, Ping, Text}
import org.http4s.websocket.WebSocketFrame

import scala.concurrent.ExecutionContext
import _root_.cats.effect.kernel.Concurrent
import _root_.cats.syntax.all._
import _root_.fs2.{Pipe, Stream as FS2Stream}
import _root_.fs2.Chunk
import _root_.scodec.bits.ByteVector
import org.http4s.Uri.Path
import org.typelevel.ci.CIString

import java.nio.charset.StandardCharsets

package object http4s {

  object CloseCode {
    val NormalClosure = 1000
  }

  def http4sKorolevService[F[_] : Effect : Concurrent, S: StateSerializer : StateDeserializer, M]
  (config: KorolevServiceConfig[F, S, M])
  (implicit ec: ExecutionContext): HttpRoutes[F] = {

    val dsl: Http4sDsl[F] = Http4sDsl[F]
    import dsl._

    val korolevServer = korolev.server.korolevService(config)

    HttpRoutes.of[F] {
      case wsReq@GET -> path if containsUpgradeHeader(wsReq) =>
        routeWsRequest(wsReq, path, korolevServer)

      case otherReqs =>
        routeHttpRequest(otherReqs, korolevServer)
    }
  }

  private final val ConnectionHeader = CIString("Connection")
  private final val UpgradeHeader = CIString("Upgrade")

  private def containsUpgradeHeader[F[_] : Effect : Concurrent](req: Request[F]): Boolean = {
    val headers = req.headers.headers
    val found = for {
      _ <- headers.find(h => h.name == ConnectionHeader && h.value.toLowerCase.indexOf("upgrade") > -1)
      _ <- headers.find(h => h.name == UpgradeHeader && h.value.toLowerCase == "websocket")
    } yield {}
    found.isDefined
  }

  private def routeWsRequest[F[_] : Effect : Concurrent, S: StateSerializer : StateDeserializer, M]
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

  private def makeSinkAndSubscriber[F[_] : Effect : Concurrent]() = {
    val queue = Queue[F, String]()
    val sink: Pipe[F, WebSocketFrame, Unit] = (in: FS2Stream[F, WebSocketFrame]) => {
      continuationReducer(in)
        .evalMap {
          case Text(t, true) if t != null =>
            queue.enqueue(t)
          case _: Close =>
            Concurrent[F].unit
          case f =>
            throw new Exception(s"Invalid frame type ${f.getClass.getName}")
        }.onFinalizeCase(_ => queue.close())
    }
    (sink, queue.stream)
  }

  private def continuationReducer[F[_] : Effect : Concurrent](in: FS2Stream[F, WebSocketFrame]) = {
    in
      .mapAccumulate(new StringBuilder()) {
        case (s, t: Text) if t.last && t != null =>
          val text = s.append(t.str).result()
          s.clear()
          (s, Some(Text(text)))

        case (s, t: Text) if !t.last && t != null =>
          (s.append(t.str), None)

        case (s, Continuation(data, false)) =>
          val newState = s.append(new String(data.toArray, StandardCharsets.UTF_8))
          (newState, None)

        case (s, Continuation(data, true)) =>
          val text = s.append(new String(data.toArray, StandardCharsets.UTF_8))
          val result = Text(text.result())
          text.clear()
          (text, Some(result))

        case (s, _: Close) =>
          (s, Some(Close))

        case (s, _: Ping) =>
          (s, None)

        case (s, f) =>
          throw new Exception(s"Invalid frame type ${f.getClass.getName}")
      }
      .collect { case (_, Some(dt: WebSocketFrame)) => dt }
  }

  private def routeHttpRequest[F[_] : Effect : Concurrent]
  (request: Request[F], korolevServer: KorolevService[F])
  (implicit ec: ExecutionContext): F[Response[F]] = {

    val dsl: Http4sDsl[F] = Http4sDsl[F]
    import dsl._

    request match {
      case req@GET -> path =>
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

  private def handleHttpResponse[F[_] : Effect : Concurrent](korolevServer: KorolevService[F],
                                                                   korolevRequest: KorolevHttpRequest[F])
                                                                  (implicit ec: ExecutionContext) = {
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
          headers = Headers(headers),
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
    val cookies = request.headers.get(CIString("cookie")).map(_.head.value)
    KorolevRequest(
      pq = PQ.fromString(path).withParams(request.params),
      method = KorolevRequest.Method.fromString(request.method.name),
      renderedCookie = cookies.orNull,
      contentLength = request.headers.get[`Content-Length`].map(_.length),
      headers = {
        val contentType = request.contentType
        val contentTypeHeaders = {
          contentType.map { ct =>
            if (ct.mediaType.isMultipart) Seq("content-type" -> contentType.toString) else Seq.empty
          }.getOrElse(Seq.empty)
        }
        request.headers.headers.map(h => (h.name.toString, h.value)) ++ contentTypeHeaders
      },
      body = body
    )
  }

}
