package korolev

import bridge.JSAccess
import korolev.Dux.Reducer
import korolev.Korolev.InitRender
import korolev.vaska.JsonQueuedJsAccess
import org.http4s._
import org.http4s.dsl._
import org.http4s.headers.`Content-Type`
import org.http4s.server.ServerBuilder
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.websocket._
import org.http4s.websocket.WebsocketBits._

import scala.concurrent.ExecutionContext
import scala.io.Source
import scalaz.concurrent.Task
import scalaz.stream.async.unboundedQueue
import scalaz.stream.{Exchange, Process, Sink}

object KorolevServer {

  val htmlContentType = Some(`Content-Type`(MediaType.`text/html`))

  def apply[State, Action](
      host: String = ServerBuilder.DefaultHost,
      port: Int = 7181,
      initialState: State,
      reducer: Reducer[State, Action],
      initRender: InitRender[State, Action]
  )(implicit ec: ExecutionContext): KorolevServer[State, Action] = {
    new KorolevServer(host, port, initialState, reducer, initRender)
  }
}

class KorolevServer[State, Action](
    host: String = ServerBuilder.DefaultHost,
    port: Int = 7181,
    initialState: State,
    reducer: Dux.Reducer[State, Action],
    initRender: Korolev.InitRender[State, Action]
)(implicit ec: ExecutionContext) {

  import KorolevServer._

  private lazy val indexHtml: String = {
    val korolevJs = {
      val stream =
        classOf[Korolev].getClassLoader.getResourceAsStream("korolev.js")
      Source.fromInputStream(stream).mkString
    }
    val bridgeJsStream = {
      val stream =
        classOf[JSAccess].getClassLoader.getResourceAsStream("bridge.js")
      Source.fromInputStream(stream).mkString
    }
    s"""
       |<html>
       |<head>
       |<script>$bridgeJsStream</script>
       |<script>$korolevJs</script>
       |</head>
       |<body>
       |</body>
       |</html>
    """.stripMargin
  }

  private val route = HttpService {
    case _ -> Root =>
      Ok(indexHtml).withContentType(htmlContentType)
    case req @ _ -> Root / "bridge" =>
      val outgoingQueue = unboundedQueue[String]
      val outgoingProcess = outgoingQueue.dequeue
        .map(s => Text(s))
        .onComplete(Process.eval_(Task.delay(Console.println("Closed"))))
      val jSAccess =
        new JsonQueuedJsAccess(s => outgoingQueue.enqueueOne(s).run)
      val sink: Sink[Task, WebSocketFrame] = Process.constant {
        case Text(t, _) => Task.fork(Task.now(jSAccess.receive(t)))
        case _ => Task.now(())
      }
      Korolev(jSAccess, initialState, reducer, initRender)
      WS(Exchange(outgoingProcess, sink))
  }

  BlazeBuilder
    .bindHttp(port)
    .withWebSockets(true)
    .mountService(route)
    .start
    .run
  Thread.currentThread().join()

}
