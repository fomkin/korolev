package korolev

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.Executors

import korolev.server.{KorolevServiceConfig, MimeTypes, Request => KorolevRequest, Response => KorolevResponse}
import org.http4s.blaze.channel._
import org.http4s.blaze.channel.nio2.NIO2SocketServerGroup
import org.http4s.blaze.http._
import org.http4s.blaze.pipeline.{Command, LeafBuilder}
import org.http4s.websocket.WebsocketBits._

import scala.concurrent.{ExecutionContext, Promise}
import scala.language.higherKinds

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
package object blazeServer {

  implicit val defaultExecutor = ExecutionContext.
    fromExecutorService(Executors.newWorkStealingPool())

  def blazeService[F[+_]: Async, S, M]: BlazeServiceBuilder[F, S, M] =
    new BlazeServiceBuilder(server.mimeTypes)

  def blazeService[F[+_]: Async, S, M](mimeTypes: MimeTypes): BlazeServiceBuilder[F, S, M] =
    new BlazeServiceBuilder(server.mimeTypes)

  def blazeService[F[+_]: Async, S, M](
    config: KorolevServiceConfig[F, S, M],
    mimeTypes: MimeTypes
  ): HttpService = {

    val korolevServer = korolev.server.korolevService(config)

    (_, uri, headers, _) => {
      val (path, params) = {
        val pi = uri.indexOf('?')
        if (pi > -1) {
          val params = uri.substring(pi + 1).split('&') map { pair =>
            val vi = pair.indexOf('=')
            if (vi == -1) (pair, "")
            else (pair.substring(0, vi), pair.substring(vi + 1))
          }
          uri.substring(0, pi) -> params.toMap
        } else {
          // All uri is path
          (uri, Map.empty[String, String])
        }
      }
      val korolevRequest = KorolevRequest(
        path = Router.Path.fromString(path),
        params,
        cookie = { key =>
          headers collectFirst {
            case ("cookie", cookieExtractor(map))
              if map.contains(key) => map(key)
          }
        }
      )

      val responseF = Async[F].map(korolevServer(korolevRequest)) {
        case KorolevResponse.Http(stream, ext, maybeDevice) =>
          val array = new Array[Byte](stream.available)
          stream.read(array)
          HttpResponse.Ok(
            body = array,
            headers = {
              val contentTypeHeader = mimeTypes(ext).map(x => "content-type" -> x)
              val deviceHeader = maybeDevice.map(x => "set-cookie" -> s"device=$x")
              Seq(contentTypeHeader, deviceHeader).flatten
            }
          )
        case KorolevResponse.WebSocket(publish, subscribe, destroy) =>
          // TODO handle disconnect on failure
          val stage = new WebSocketStage {
            def onDirtyDisconnect(e: Throwable): Unit = destroy()
            def onMessage(msg: WebSocketFrame): Unit = msg match {
              case Text(incomingMessage, _) => publish(incomingMessage)
              case Binary(_, _) => // ignore
              case Close(_) =>
                destroy()
                sendOutboundCommand(Command.Disconnect)
            }
            override protected def stageStartup(): Unit = {
              super.stageStartup()
              subscribe { outgoingMessage =>
                channelWrite(Text(outgoingMessage))
              }
            }
          }
          WSResponse(WebSocketStage.bufferingSegment(stage))
      }

      val promise = Promise[Response]()
      Async[F].run(responseF)(promise.complete)
      promise.future
    }
  }

  def runServer(
    service: HttpService,
    config: BlazeServerConfig
  ): Unit = {

    val f: BufferPipelineBuilder = _ => LeafBuilder(new HttpServerStage(1024*1024, 10*1024)(service))
    val group = AsynchronousChannelGroup.withThreadPool(config.executionContext)
    val factory = NIO2SocketServerGroup(config.bufferSize, Some(group))

    factory.
      bind(new InetSocketAddress(config.port), f).
      getOrElse(sys.error("Failed to bind server")).
      join()
  }

  private object cookieExtractor {
    def unapply(arg: String): Option[Map[String, String]] = Some {
      arg.split(';') map { part =>
        val Array(name, content) = part.split("=", 1)
        name.trim -> content.trim
      } toMap
    }
  }
}
