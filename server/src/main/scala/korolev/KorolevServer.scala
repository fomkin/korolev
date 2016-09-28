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

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}
import scalaz.stream.async.unboundedQueue
import scalaz.stream.{Exchange, Process, Sink}
import scalaz.stream.DefaultScheduler
import scalaz.stream.time

object KorolevServer {

  val htmlContentType = Some(`Content-Type`(MediaType.`text/html`))

  val defaultRead = Future.successful(None)

  def apply[State](
      host: String = ServerBuilder.DefaultHost,
      port: Int = 7181,
      initialState: State,
      initRender: InitRender[State],
      head: VDom.Node = VDom.Node("head", Nil, Nil, Nil),
      writeState: (String, State) => _ = (_: String, _: State) => (),
      readState: String => Future[Option[State]] = (_: String) => defaultRead
  )(implicit ec: ExecutionContext): KorolevServer[State] = {
    new KorolevServer(host, port, initialState, initRender, head, writeState, readState)
  }
}

class KorolevServer[State](
    host: String = ServerBuilder.DefaultHost,
    port: Int = 7181,
    initialState: State,
    initRender: Korolev.InitRender[State],
    head: VDom.Node,
    writeState: (String, State) => _,
    readState: String => Future[Option[State]]
)(implicit ec: ExecutionContext) extends Shtml {

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

  private val route = HttpService {
    case _ -> Root =>
      Ok(indexHtml).withContentType(htmlContentType)
    case req @ _ -> Root / "bridge" =>
      val outgoingQueue = unboundedQueue[WebSocketFrame]
      val outgoingProcess = outgoingQueue.dequeue
        .onComplete(Process.eval_(Task.delay(Console.println("Closed"))))
      time.awakeEvery(5.seconds)(Strategy.DefaultStrategy, DefaultScheduler)
        .map(_ => Ping())
        .to(outgoingQueue.enqueue)
        .run.runAsync(_ => ())
      val jSAccess =
        new JsonQueuedJsAccess(s => outgoingQueue.enqueueOne(Text(s)).run)
      val sink: Sink[Task, WebSocketFrame] = Process.constant {
        case Text(t, _) => Task.fork(Task.now(jSAccess.receive(t)))
        case _ => Task.now(())
      }
      Korolev(jSAccess, initialState, initRender)
      WS(Exchange(outgoingProcess, sink))
  }

  BlazeBuilder
    .withNio2(true)
    .bindHttp(port)
    .withWebSockets(true)
    .mountService(route)
    .start
    .run
  Thread.currentThread().join()

}
