package korolev

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousChannelGroup

import korolev.server.{KorolevServiceConfig, MimeTypes, Request => KorolevRequest, Response => KorolevResponse}
import korolev.util.Scheduler
import org.http4s.blaze.channel._
import org.http4s.blaze.channel.nio2.NIO2SocketServerGroup
import org.http4s.blaze.http.{HttpResponse, HttpService, Response, WSResponse}
import org.http4s.blaze.pipeline.stages.SSLStage
import org.http4s.blaze.pipeline.{Command, LeafBuilder}
import org.http4s.websocket.WebsocketBits._

import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.language.higherKinds

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
package object blazeServer {

  def blazeService[F[+_]: Async, S, M]: BlazeServiceBuilder[F, S, M] =
    new BlazeServiceBuilder(server.mimeTypes)

  def blazeService[F[+_]: Async, S, M](mimeTypes: MimeTypes): BlazeServiceBuilder[F, S, M] =
    new BlazeServiceBuilder(mimeTypes)

  def blazeService[F[+_]: Async, S, M](
    config: KorolevServiceConfig[F, S, M],
    mimeTypes: MimeTypes
  )(implicit scheduler: Scheduler[F]): HttpService = {

    val korolevServer = korolev.server.korolevService(mimeTypes, config)

    (_, uri, headers, body) => {
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
            /* Different browsers can send http-headers with upper-case first literal,
             * or whole word in lower-case
             * */
            case ("Cookie" | "сookie", cookieExtractor(map))
              if map.contains(key) => map(key)
          }
        },
        body = {
          val l = body.remaining()
          val bytes = new Array[Byte](l)
          if (l > 0) body.get(bytes)
          ByteBuffer.wrap(bytes)
        },
        headers = headers
      )

      val responseF = Async[F].map(korolevServer(korolevRequest)) {
        case KorolevResponse.Http(status, streamOpt, responseHeaders) =>
          val array = streamOpt match {
            case Some(stream) =>
              val array = new Array[Byte](stream.available)
              var offset = 0
              while (stream.available > 0) {
                offset += stream.read(array, offset, stream.available)
              }
              array
            case None =>
              Array.empty[Byte]
          }
          HttpResponse(status.code, status.phrase, responseHeaders, ByteBuffer.wrap(array))
        case KorolevResponse.WebSocket(publish, subscribe, destroy) =>
          val stage = new WebSocketStage {
            val stopHeartbeat = scheduler.schedule(5.seconds) {
              channelWrite(Ping())
            }
            def destroyAndStopTimer(): Unit = {
              stopHeartbeat()
              destroy()
            }
            def onDirtyDisconnect(e: Throwable): Unit = destroyAndStopTimer()
            def onMessage(msg: WebSocketFrame): Unit = msg match {
              case Text(incomingMessage, _) => publish(incomingMessage)
              case Close(_) =>
                destroyAndStopTimer()
                sendOutboundCommand(Command.Disconnect)
              case _ => // ignore
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
  ): ServerChannel = {

    val f: BufferPipelineBuilder = _ => {
      def serviceStage = LeafBuilder(new HttpServerStage(1024*1024, 10*1024)(service))
      config.sslContext match {
        case Some(sslContext) =>
          val eng = sslContext.createSSLEngine()
          eng.setUseClientMode(false)
          serviceStage.prepend(new SSLStage(eng))
        case None => serviceStage
      }
    }

    val group = AsynchronousChannelGroup.withThreadPool(config.executionContext)
    val factory = NIO2SocketServerGroup(config.bufferSize, Some(group))

    val serverChannel = factory.
      bind(new InetSocketAddress(config.port), f).
      getOrElse(sys.error("Failed to bind server"))

    if (!config.doNotBlockCurrentThread)
      serverChannel.join()

    serverChannel
  }

  private object cookieExtractor {
    def unapply(arg: String): Option[Map[String, String]] = Some {
      arg.split(';') map { part =>
        val Array(name, content) = part.split("=", 2)
        name.trim -> content.trim
      } toMap
    }
  }
}
