package korolev.http

import java.net.InetSocketAddress
import java.nio.ByteBuffer

import korolev.data.BytesLike
import korolev.effect.io.RawDataSocket
import korolev.effect.syntax._
import korolev.effect.{Decoder, Effect, Stream}
import korolev.http.protocol.{Http11, WebSocketProtocol}
import korolev.web.{Headers, Path, Request, Response, Uri}

import scala.concurrent.ExecutionContext

object HttpClient {

  def apply[F[_]: Effect, B: BytesLike](host: String,
                                        port: Int,
                                        request: Request[Stream[F, B]],
                                        bufferSize: Int = 8096)
                                       (implicit executor: ExecutionContext): F[Response[Stream[F, B]]] = {
    val http11 = new Http11[B]()

    for {
      socket <- RawDataSocket.connect(new InetSocketAddress(host, port), buffer = ByteBuffer.allocate(bufferSize))
      requestStream <- http11.renderRequest(request.withHeader(Headers.Host, host))
      writeBytesFiber <- requestStream.foreach(socket.write).start // Write response asynchronously
      maybeResponse <- http11.decodeResponse(Decoder(socket.stream)).pull()
      response <-
        maybeResponse match {
          case Some(response) =>
            val (consumed, consumableBody) = response.body.handleConsumed
            consumed
              .after(writeBytesFiber.join())
              // Close socket when body was consumed
              // TODO socket connections should be recycled
              .after(socket.stream.cancel())
              .start
              .as(response.copy(body = consumableBody))
          case None =>
            Effect[F].fail[Response[Stream[F, B]]](
              new IllegalStateException("Peer has closed connection before sending response."))
        }
    } yield response
  }

  def webSocket[F[_]: Effect, B: BytesLike](host: String,
                              port: Int,
                              path: Path,
                              outgoingFrames: Stream[F, WebSocketProtocol.Frame[B]],
                              params: Map[String, String] = Map.empty,
                              cookie: Map[String, String] = Map.empty,
                              headers: Map[String, String] = Map.empty)
                             (implicit executor: ExecutionContext): F[Response[Stream[F, WebSocketProtocol.Frame.Merged[B]]]] = {
    val webSocketProtocol = new WebSocketProtocol[B]()
    for {
      intention <- WebSocketProtocol.Intention.random
      encodedOutgoingFrames = outgoingFrames.map(frame => webSocketProtocol.encodeFrame(frame, Some(123)))
      requestRaw = Request(Request.Method.Get, Uri(path), headers.toSeq, None, encodedOutgoingFrames)
      requestWithParams = params.foldLeft(requestRaw) { case (acc, (k, v)) => acc.withParam(k, v) }
      requestWithCookie = cookie.foldLeft(requestWithParams) { case (acc, (k, v)) => acc.withCookie(k, v) }
      requestWithIntention = webSocketProtocol.addIntention(requestWithCookie, intention)
      rawResponse <- HttpClient(host, port, requestWithIntention)
      frameDecoder = Decoder(rawResponse.body)
      frames = webSocketProtocol.mergeFrames(webSocketProtocol.decodeFrames(frameDecoder))
    } yield {
      rawResponse.copy(body = frames)
    }
  }
}
