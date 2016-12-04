package korolev

import java.util.UUID

import bridge.JSAccess
import korolev.Korolev.InitRender
import org.http4s._
import org.http4s.dsl._
import org.http4s.headers.`Content-Type`
import org.http4s.server.ServerBuilder
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.websocket._
import org.http4s.websocket.WebsocketBits._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future}
import scala.io.Source
import scalaz.concurrent.{Strategy, Task}
import scalaz.stream.async.unboundedQueue
import scalaz.stream.{DefaultScheduler, Exchange, Process, Sink, time}
import delorean._
import korolev.Event.Phase
import org.slf4j.LoggerFactory

object KorolevServer {

  val htmlContentType = Some(`Content-Type`(MediaType.`text/html`))

  val defaultEc = ExecutionContext.fromExecutorService(Strategy.DefaultExecutorService)

  def apply[State](
      host: String = ServerBuilder.DefaultHost,
      port: Int = 7181,
      initRender: InitRender[State],
      stateStorage: StateStorage[State],
      head: VDom.Node = VDom.Node("head", Nil, Nil, Nil)
  )(implicit executor: ExecutionContextExecutorService = defaultEc): KorolevServer[State] = {
    new KorolevServer(host, port, initRender, head, stateStorage)
  }
}

class KorolevServer[State](
    host: String,
    port: Int,
    initRender: Korolev.InitRender[State],
    head: VDom.Node,
    stateStorage: StateStorage[State]
)(implicit executor: ExecutionContextExecutorService) extends Shtml { self =>

  private val logger = LoggerFactory.getLogger(KorolevServer.getClass)

  implicit val strategy: Strategy = Strategy.Executor

  import KorolevServer._

  private val korolevJs = {
    val stream =
      classOf[Korolev].getClassLoader.getResourceAsStream("korolev.js")
    Source.fromInputStream(stream).mkString
  }

  private val bridgeJs = {
    val stream =
      classOf[JSAccess].getClassLoader.getResourceAsStream("bridge.js")
    Source.fromInputStream(stream).mkString
  }

  private def indexHtml(request: Request) = {
    val (isNewDevice, deviceId) = deviceFromRequest(request)
    val stateTask = stateStorage.initial(deviceId).toTask
    val htmlTask = stateTask map { state =>
      val render = initRender(KorolevAccess.dummy)
      val body = render(state)
      val dom = 'html(
        head.copy(children =
          'script(bridgeJs) ::
          'script(korolevJs) ::
          head.children
        ),
        body
      )
      "<!DOCTYPE html>" + dom.html
    }

    if (isNewDevice) {
      Ok(htmlTask)
        .addCookie("device", deviceId)
        .withContentType(htmlContentType)
    } else {
      Ok(htmlTask)
        .withContentType(htmlContentType)
    }
  }

  private object matchStatic {
    def unapply(req: Request) = {
      val path = req.pathInfo
      val stream = getClass.getResourceAsStream(s"/static$path")

      Option(stream) map { stream =>
        val contentType = {
          val index = path.lastIndexOf('.')
          val mediaType = if (index > -1) {
            MediaType.forExtension(path.substring(index + 1)) match {
              case Some(detectedMediaType) => detectedMediaType
              case None => MediaType.`application/octet-stream`
            }
          } else {
            MediaType.`application/octet-stream`
          }
          Some(`Content-Type`(mediaType))
        }
        (stream, contentType)
      }
    }
  }

  private def deviceFromRequest(request: Request): (Boolean, String) = {
    def genDeviceId() = true -> UUID.randomUUID().toString
    // Check cookies
    request.headers.get(org.http4s.headers.Cookie) match {
      case None =>
        // Cookies are empty. Create new one.
        genDeviceId()
      case Some(cookies) =>
        // Lets find device cookie
        val uuidOpt = cookies.values.collectFirst {
          case cookie: org.http4s.Cookie if cookie.name == "device" =>
            cookie.content
        }
        // Create UUID from cookie (if found)
        // or create new one
        uuidOpt.fold(genDeviceId)(uuid => false -> uuid)
    }
  }

  private val route = HttpService {

    case request @ _ -> Root => indexHtml(request)
    case request @ _ -> Root / "bridge" / deviceId / sessionId =>

      val outgoingQueue = unboundedQueue[WebSocketFrame]

      time.awakeEvery(5.seconds)(strategy, DefaultScheduler)
        .map(_ => Ping())
        .to(outgoingQueue.enqueue)
        .run.runAsync(_ => ())

      val jSAccess =
        new JsonQueuedJsAccess(s => outgoingQueue.enqueueOne(Text(s)).run)

      val sink: Sink[Task, WebSocketFrame] = Process.constant {
        case Text(t, _) => Task.fork(Task.now(jSAccess.receive(t)))
        case _ => Task.now(())
      }

      val korolev =  {
        stateStorage.read(deviceId, sessionId).toTask map { state =>
          val korolev = Korolev(jSAccess, state, initRender, false)
          korolev.subscribe(state => stateStorage.write(deviceId, sessionId, state))
          korolev
        }
      }

      val outgoingProcess = Process.eval(korolev) flatMap { korolev =>
        outgoingQueue.dequeue onComplete {
          val task = Task.delay(korolev.destroy())
          Process.eval_(task)
        }
      }

      WS(Exchange(outgoingProcess, sink))

    case matchStatic(stream, contentType) =>
      Ok(stream).withContentType(contentType)
  }

  BlazeBuilder
    .withNio2(true)
    .bindHttp(port, host)
    .withWebSockets(true)
    .mountService(route)
    .withServiceExecutor(executor)
    .start
    .run

  Thread.currentThread().join()

}
