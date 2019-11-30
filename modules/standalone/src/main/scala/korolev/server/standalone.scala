package korolev.server

import java.net.SocketAddress
import java.nio.channels.AsynchronousChannelGroup

import korolev.data.ByteVector
import korolev.effect.io.{LazyBytes, ServerSocket}
import korolev.effect.syntax._
import korolev.effect.{Effect, Stream}
import korolev.http.HttpServer
import korolev.http.protocol.WebSocketProtocol
import korolev.web.Request

import scala.concurrent.ExecutionContext

object standalone {

  def buildServer[F[_]: Effect](service: KorolevService[F],
                                address: SocketAddress,
                                group: AsynchronousChannelGroup = null)
                               (implicit ec: ExecutionContext): F[ServerSocket.ServerSocketHandler[F]] = {
    HttpServer(address, group = group) { request =>
      WebSocketProtocol.findIntention(request) match {
        case Some(intention) =>
          val f = WebSocketProtocol.upgrade[F](intention) { (request: Request[Stream[F, WebSocketProtocol.Frame.Merged]]) =>
            val b2 = request.body.collect {
              case WebSocketProtocol.Frame.Text(message, _) =>
                message.utf8String
            }
            // TODO service.ws should work with websocket frame
            service.ws(request.copy(body = b2)).map { x =>
              x.copy(body = x.body.map(m => WebSocketProtocol.Frame.Text(ByteVector.utf8(m), fin = true)))
            }
          }
          f(request.copy(body = LazyBytes(request.body.map(bv => bv.mkArray), request.contentLength)))
            .map(response => response.copy(body = response.body.chunks.map(ByteVector(_))))
        case _ =>
          // This is just HTTP query
          service.http(request.copy(body = LazyBytes(request.body.map(bv => bv.mkArray), request.contentLength)))
            .map(response => response.copy(body = response.body.chunks.map(ByteVector(_))))
      }
    }
  }

}
