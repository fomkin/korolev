package korolev

import java.util.UUID

import bridge.JSAccess
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
import korolev.StateStorage.{DeviceId, SessionId}
import org.slf4j.LoggerFactory

object KorolevServer {

  val htmlContentType = Some(`Content-Type`(MediaType.`text/html`))

  val defaultEc = ExecutionContext.fromExecutorService(Strategy.DefaultExecutorService)

  def apply[State](
      host: String = ServerBuilder.DefaultHost,
      port: Int = 7181,
      render: Korolev.Render[State],
      stateStorage: StateStorage[State],
      router: ServerRouter[Future, State] =
        ServerRouter.empty[Future, State](Async.futureAsync(defaultEc)),
      head: VDom.Node = VDom.Node("head", Nil, Nil, Nil)
  )(implicit executor: ExecutionContextExecutorService = defaultEc): KorolevServer[State] = {
    new KorolevServer(host, port, render, head, router, stateStorage)
  }
}

class KorolevServer[State](
    host: String,
    port: Int,
    render: Korolev.Render[State],
    head: VDom.Node,
    serverRouter: ServerRouter[Future, State],
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
      classOf[JSAccess[Future]].getClassLoader.getResourceAsStream("bridge.js")
    Source.fromInputStream(stream).mkString
  }

  private def indexHtml(request: Request, path: String) = {
    val (isNewDevice, deviceId) = deviceFromRequest(request)
    val stateTask = serverRouter.
      static(deviceId).
      toState.lift(((), Router.Path.fromString(path))).
      getOrElse(stateStorage.initial(deviceId)).
      toTask

    val htmlTask = stateTask map { state =>
      val body = render(state)
      val dom = 'html(
        head.copy(children =
          'script(bridgeJs) ::
          'script(
            s"var KorolevServerRootPath = '${serverRouter.rootPath}';\n" +
            korolevJs
          ) ::
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

    case request @ _ -> Root / "bridge" / deviceId / sessionId =>
      val outgoingQueue = unboundedQueue[WebSocketFrame]
      time.awakeEvery(5.seconds)(strategy, DefaultScheduler)
        .map(_ => Ping())
        .to(outgoingQueue.enqueue)
        .run.runAsync(_ => ())
      val jSAccess = new JsonQueuedJsAccess(s => outgoingQueue.enqueueOne(Text(s)).run)
      val sink: Sink[Task, WebSocketFrame] = Process.constant {
        case Text(t, _) => Task.fork(Task.now(jSAccess.receive(t)))
        case _ => Task.now(())
      }
      val korolev =  {
        stateStorage.read(deviceId, sessionId).toTask map { state =>
          val router = serverRouter.dynamic(deviceId, sessionId)
          val korolev = Korolev(jSAccess, state, render, router, false)
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

    case request =>
      indexHtml(request, request.pathInfo)
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
