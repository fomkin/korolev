package korolev.http

import java.net.SocketAddress
import java.nio.channels.AsynchronousChannelGroup

import korolev.data.ByteVector
import korolev.effect.io.ServerSocket
import korolev.effect.syntax._
import korolev.effect.{Decoder, Effect, Stream}
import korolev.http.protocol.Http11
import korolev.web.{Request, Response}

import scala.concurrent.ExecutionContext

object HttpServer {

  /**
    * @see [[ServerSocket.bind]]
    */
  def apply[F[_]: Effect](address: SocketAddress,
                          backlog: Int = 0,
                          readBufferSize: Int = 8096,
                          group: AsynchronousChannelGroup = null)
                         (f: Request[Stream[F, ByteVector]] => F[Response[Stream[F, ByteVector]]])
                         (implicit ec: ExecutionContext): F[ServerSocket.ServerSocketHandler[F]] = {
    ServerSocket.accept(address, backlog, readBufferSize, group) { client =>
      Http11
        .decodeRequest(Decoder(client.stream))
        .foreach { request =>
          for {
            response <- f(request)
            byteStream <- Http11.renderResponse(response)
            _ <- byteStream.foreach(client.write)
          } yield ()
        }
    }
  }
}

