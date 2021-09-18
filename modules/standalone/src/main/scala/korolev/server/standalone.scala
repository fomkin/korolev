package korolev.server

import java.net.SocketAddress
import java.nio.channels.AsynchronousChannelGroup
import korolev.data.{Bytes, BytesLike}
import korolev.data.syntax._
import korolev.effect.io.ServerSocket
import korolev.effect.syntax._
import korolev.effect.{Effect, Stream}
import korolev.http.HttpServer
import korolev.http.protocol.WebSocketProtocol
import korolev.web.Request

import scala.concurrent.ExecutionContext

object standalone {

  def buildServer[F[_]: Effect, B: BytesLike](service: KorolevService[F],
                                              address: SocketAddress,
                                              group: AsynchronousChannelGroup = null,
                                              gracefulShutdown: Boolean)
                                             (implicit ec: ExecutionContext): F[ServerSocket.ServerSocketHandler[F]] = {
    val webSocketProtocol = new WebSocketProtocol[B]
    HttpServer[F, B](address, group = group, gracefulShutdown = gracefulShutdown) { request =>
      webSocketProtocol.findIntention(request) match {
        case Some(intention) =>
          val f = webSocketProtocol.upgrade[F](intention) { (request: Request[Stream[F, WebSocketProtocol.Frame.Merged[B]]]) =>
            val b2 = request.body.collect {
              case WebSocketProtocol.Frame.Text(message, _) =>
                message.asUtf8String
            }
            // TODO service.ws should work with websocket frame
            service.ws(request.copy(body = b2)).map { x =>
              x.copy(body = x.body.map(m => WebSocketProtocol.Frame.Text(BytesLike[B].utf8(m))))
            }
          }
          f(request)
        case _ =>
          // This is just HTTP query
          service
            .http(request.copy(body = request.body.map(Bytes.wrap(_))))
            .map(response => response.copy(body = response.body.map(_.as[B])))
      }
    }
  }

}
