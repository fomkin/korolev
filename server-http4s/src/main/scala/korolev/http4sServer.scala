package korolev

import korolev.scalazSupport._
import korolev.server.{
  ServerRouter,
  StateStorage,
  Request => KorolevRequest,
  Response => KorolevResponse
}
import org.http4s.dsl._
import org.http4s.headers.{`Content-Type`, Cookie => CookerHeader}
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.websocket._
import org.http4s.server.{Server, ServerApp, ServerBuilder}
import org.http4s.websocket.WebsocketBits.{Ping, Text, WebSocketFrame}
import org.http4s.{Cookie, HttpService, MediaType}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}
import scalaz.concurrent.{Strategy, Task}
import scalaz.stream.async.unboundedQueue
import scalaz.stream.{DefaultScheduler, Exchange, Process, Sink, time}

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
object http4sServer {

  val defaultExecutorService =
    ExecutionContext.fromExecutorService(Strategy.DefaultExecutorService)

  def configureHttpService[S](
      // Without defaults
      stateStorage: StateStorage[Task, S],
      // With defaults
      render: Korolev.Render[S] = PartialFunction.empty,
      head: VDom.Node = VDom.Node("head", Nil, Nil, Nil),
      serverRouter: ServerRouter[Task, S] = ServerRouter.empty[Task, S],
      executor: ExecutionContextExecutorService = defaultExecutorService
  ): HttpService = {

    implicit val strategy: Strategy = Strategy.Executor(executor)

    val korolevServer = korolev.server.configureServer(
      render,
      head,
      stateStorage,
      serverRouter = serverRouter
    )

    HttpService {
      case http4sRequest =>
        val korolevRequest = KorolevRequest(
          path = Router.Path.fromString(http4sRequest.pathInfo),
          http4sRequest.params,
          cookie = { key =>
            http4sRequest.headers.get(CookerHeader) flatMap { cookies =>
              cookies.values.collectFirst {
                case cookie: Cookie if cookie.name == key =>
                  cookie.content
              }
            }
          }
        )

        def contentTypeForExt(ext: String) = {
          MediaType.forExtension(ext) map { mediaType =>
            `Content-Type`(mediaType)
          }
        }

        korolevServer(korolevRequest) flatMap {

          case KorolevResponse.HttpResponse(body, ext, Some(deviceId)) =>
            Ok(body)
              .withContentType(contentTypeForExt(ext))
              .addCookie("device", deviceId)

          case KorolevResponse.HttpResponse(body, ext, None) =>
            Ok(body).withContentType(contentTypeForExt(ext))

          case KorolevResponse.WebSocket(publish, subscribe, destroy) =>
            val outgoingQueue = unboundedQueue[WebSocketFrame]
            time
              .awakeEvery(5.seconds)(strategy, DefaultScheduler)
              .map(_ => Ping())
              .to(outgoingQueue.enqueue)
              .run
              .runAsync(_ => ())
            val sink: Sink[Task, WebSocketFrame] = Process.constant {
              case Text(t, _) => Task.fork(Task.now(publish(t)))
              case _ => Task.now(())
            }
            subscribe(s => outgoingQueue.enqueueOne(Text(s)).run)
            val outgoingProcess = outgoingQueue.dequeue onComplete {
              Process.eval_(Task.delay(destroy()))
            }
            WS(Exchange(outgoingProcess, sink))
        }
    }
  }

  /**
    * Create blaze server from HttpService.
    *
    * @see configureHttpService
    */
  def buildServer(
      service: HttpService,
      port: Int = 8181,
      host: String = ServerBuilder.DefaultHost,
      executor: ExecutionContextExecutorService = defaultExecutorService
  ): BlazeBuilder = {
    BlazeBuilder
      .withNio2(true)
      .bindHttp(port, host)
      .withWebSockets(true)
      .mountService(service)
      .withServiceExecutor(executor)
  }

  /**
    * Boilerplate for your http4s based Korolev-servers.
    *
    * @see configureHttpService
    * @example
    * ```scala
    * import import korolev.http4sServer.configureHttpService
    * import korolev.scalazSupport._
    *
    * object Main extends KorolevHttp4sServerApp {
    *   val service = configureHttpService(...)
    * }
    * ```
    */
  trait KorolevHttp4sServerApp extends ServerApp {

    def service: HttpService

    def server(args: List[String]): Task[Server] = {
      buildServer(service).start
    }
  }

}
