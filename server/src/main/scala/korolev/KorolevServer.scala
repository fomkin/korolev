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
      initialState: State,
      initRender: InitRender[State],
      head: VDom.Node = VDom.Node("head", Nil, Nil, Nil),
      stateStorage: StateStorage[State] = StateStorage.inMemory[State]
  )(implicit executor: ExecutionContextExecutorService = defaultEc): KorolevServer[State] = {
    new KorolevServer(host, port, initialState, initRender, head, stateStorage)
  }
}

class KorolevServer[State](
    host: String,
    port: Int,
    initialState: State,
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
    val sessionPair @ (isNewSession, sessionId) = sessionFromRequest(request)
    val stateTask = sessionPair match {
      case (true, sessionId) =>
        Task.now(initialState)
      case (false, sessionId) =>
        stateStorage
          .read(sessionId)
          .toTask
          .flatMap {
            case Some(restoredState) => Task.now(restoredState)
            case None => stateStorage
              .write(sessionId, initialState)
              .toTask
              .map(_ => initialState)
          }
    }

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

    if (isNewSession) {
      Ok(htmlTask)
        .addCookie("session", sessionId)
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

  private def sessionFromRequest(request: Request): (Boolean, String) = {
    def genSessionId = true -> UUID.randomUUID().toString
    request.headers.get(org.http4s.headers.Cookie) match {
      case None =>
        // Cookies is empty. Create new one.
        genSessionId
      case Some(cookies) =>
        val uuidOpt = cookies.values.collectFirst {
          case cookie: org.http4s.Cookie if cookie.name == "session" =>
            cookie.content
        }
        // Create UUID from cookie or create new one
        uuidOpt.fold(genSessionId)(uuid => false -> uuid)
    }
  }

  private val route = HttpService {

    case request @ _ -> Root => indexHtml(request)
    case request @ _ -> Root / "bridge" =>

      val (isNewSession, sessionId) = sessionFromRequest(request)
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

      logger.info(isNewSession.toString)

      val korolev = if (isNewSession) {
        Task.now(Korolev(jSAccess, initialState, initRender, isNewSession))
      } else {
        stateStorage.read(sessionId).toTask map { stateOpt =>
          val state = stateOpt match {
            case Some(restoredState) => Korolev(jSAccess, restoredState, initRender, isNewSession)
            case None => Korolev(jSAccess, initialState, initRender, true)
          }
          state subscribe { x =>
            stateStorage.write(sessionId, x)
          }
          state
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
