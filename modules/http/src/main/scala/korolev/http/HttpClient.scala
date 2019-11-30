package korolev.http

import java.net.{InetSocketAddress, SocketAddress}

import korolev.data.ByteVector
import korolev.effect.io.RawDataSocket
import korolev.effect.syntax._
import korolev.effect.{Decoder, Effect, Stream}
import korolev.http.protocol.{Http11, WebSocketProtocol}
import korolev.web.{Headers, Path, Request, Response}

import scala.concurrent.ExecutionContext

object HttpClient {

  def apply[F[_]: Effect](host: String,
                          port: Int,
                          request: Request[Stream[F, ByteVector]])
                         (implicit executor: ExecutionContext): F[Response[Stream[F, ByteVector]]] = {
    for {
      socket <- RawDataSocket.connect(new InetSocketAddress(host, port))
      requestStream <- Http11.renderRequest(request.withHeader(Headers.Host, host))
      writeBytesFiber <- requestStream.foreach(socket.write).start // Write response asynchronously
      maybeResponse <- Http11.decodeResponse(Decoder(socket.stream)).pull()
      response <-
        maybeResponse match {
          case Some(response) =>
            val (consumed, consumableBody) = response.body.handleConsumed
            consumed
              .after(writeBytesFiber.join())
              .after(socket.stream.cancel())
              .start
              .as(response.copy(body = consumableBody))
          case None =>
            Effect[F].fail[Response[Stream[F, ByteVector]]](
              new IllegalStateException("Peer has closed connection before sending response."))
        }
    } yield response
  }

  def webSocket[F[_]: Effect](host: String,
                              port: Int,
                              path: Path,
                              outgoingFrames: Stream[F, WebSocketProtocol.Frame],
                              params: Map[String, String] = Map.empty,
                              cookie: Map[String, String] = Map.empty,
                              headers: Map[String, String] = Map.empty)
                             (implicit executor: ExecutionContext): F[Response[Stream[F, WebSocketProtocol.Frame.Merged]]] =
    for {
      intention <- WebSocketProtocol.Intention.random
      encodedOutgoingFrames = outgoingFrames.map(frame => WebSocketProtocol.encodeFrame(frame, Some(123)))
      requestRaw = Request(Request.Method.Get, path, headers.toSeq, None, encodedOutgoingFrames)
      requestWithParams = params.foldLeft(requestRaw) { case (acc, (k, v)) => acc.withParam(k, v) }
      requestWithCookie = cookie.foldLeft(requestWithParams) { case (acc, (k, v)) => acc.withCookie(k, v) }
      requestWithIntention = WebSocketProtocol.addIntention(requestWithCookie, intention)
      rawResponse <- HttpClient(host, port, requestWithIntention)
      frameDecoder = Decoder(rawResponse.body)
      frames = WebSocketProtocol.mergeFrames(WebSocketProtocol.decodeFrames(frameDecoder))
    } yield {
      rawResponse.copy(body = frames)
    }
}
