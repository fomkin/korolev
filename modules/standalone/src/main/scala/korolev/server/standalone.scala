package korolev.server

import java.net.SocketAddress
import java.nio.channels.AsynchronousChannelGroup

import korolev.data.BytesLike
import korolev.data.syntax._
import korolev.effect.io.{LazyBytes, ServerSocket}
import korolev.effect.syntax._
import korolev.effect.{Effect, Stream}
import korolev.http.HttpServer
import korolev.http.protocol.WebSocketProtocol
import korolev.web.Request

import scala.concurrent.ExecutionContext

object standalone {

  def buildServer[F[_]: Effect, B: BytesLike](service: KorolevService[F],
                                              address: SocketAddress,
                                              group: AsynchronousChannelGroup = null)
                                             (implicit ec: ExecutionContext): F[ServerSocket.ServerSocketHandler[F]] = {
    val webSocketProtocol = new WebSocketProtocol[B]
    HttpServer[F, B](address, group = group) { request =>
      webSocketProtocol.findIntention(request) match {
        case Some(intention) =>
          val f = webSocketProtocol.upgrade[F](intention) { (request: Request[Stream[F, WebSocketProtocol.Frame.Merged[B]]]) =>
            val b2 = request.body.collect {
              case WebSocketProtocol.Frame.Text(message, _) =>
                message.asUtf8String
            }
            // TODO service.ws should work with websocket frame
            service.ws(request.copy(body = b2)).map { x =>
              x.copy(body = x.body.map(m => WebSocketProtocol.Frame.Text(BytesLike[B].utf8(m), fin = true)))
            }
          }
          f(request.copy(body = LazyBytes(request.body.map(bv => bv.asArray), request.contentLength)))
            .map(response => response.copy(body = response.body.chunks.map(BytesLike[B].wrapArray(_))))
        case _ =>
          // This is just HTTP query
          service.http(request.copy(body = LazyBytes(request.body.map(bv => bv.asArray), request.contentLength)))
            .map(response => response.copy(body = response.body.chunks.map(BytesLike[B].wrapArray(_))))
      }
    }
  }

}
