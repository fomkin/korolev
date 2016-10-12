package korolev

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

object KorolevServer {

  val htmlContentType = Some(`Content-Type`(MediaType.`text/html`))

  val defaultRead = Future.successful(None)

  val defaultEc = ExecutionContext.fromExecutorService(Strategy.DefaultExecutorService)

  def apply[State](
      host: String = ServerBuilder.DefaultHost,
      port: Int = 7181,
      initialState: State,
      initRender: InitRender[State],
      head: VDom.Node = VDom.Node("head", Nil, Nil, Nil),
      writeState: (String, State) => _ = (_: String, _: State) => (),
      readState: String => Future[Option[State]] = (_: String) => defaultRead
  )(implicit executor: ExecutionContextExecutorService = defaultEc): KorolevServer[State] = {
    new KorolevServer(host, port, initialState, initRender, head, writeState, readState)
  }
}

class KorolevServer[State](
    host: String,
    port: Int,
    initialState: State,
    initRender: Korolev.InitRender[State],
    head: VDom.Node,
    writeState: (String, State) => _,
    readState: String => Future[Option[State]]
)(implicit executor: ExecutionContextExecutorService) extends Shtml { self =>

  implicit val strategy: Strategy = Strategy.Executor

  import KorolevServer._

  private lazy val indexHtml: String = {

    val korolevJs = {
      val stream =
        classOf[Korolev].getClassLoader.getResourceAsStream("korolev.js")
      Source.fromInputStream(stream).mkString
    }
    val bridgeJs = {
      val stream =
        classOf[JSAccess].getClassLoader.getResourceAsStream("bridge.js")
      Source.fromInputStream(stream).mkString
    }

    val dom = 'html(
      head.copy(children =
        'script(bridgeJs) ::
        'script(korolevJs) ::
        head.children
      ),
      'body()
    )

    "<!DOCTYPE html>" + dom.html
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

  private val route = HttpService {
    case _ -> Root =>
      Ok(indexHtml).withContentType(htmlContentType)
    case req @ _ -> Root / "bridge" =>
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
      val korolev = Korolev(jSAccess, initialState, initRender)
      val outgoingProcess = outgoingQueue.dequeue
        .onComplete(Process.eval_(Task delay {
          Console.println("Closed")
          korolev.destroy()
        }))
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
