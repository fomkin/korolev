import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import korolev._
import korolev.akka._
import korolev.execution._
import korolev.server._
import korolev.state.javaSerialization._

import scala.concurrent.Future

object ExtensionExample extends App {

  private implicit val actorSystem: ActorSystem = ActorSystem()
  private implicit val materializer: Materializer = ActorMaterializer()

  val applicationContext = Context[Future, List[String], String]

  import applicationContext._
  import levsha.dsl._
  import html._

  val (queue, queueSource) = Source
    .queue[String](10, OverflowStrategy.fail)
    .preMaterialize()

  private val topicListener = Extension.pure[Future, List[String], String] { access =>
    val queueSink = queueSource.runWith(Sink.queue[String])
    def aux(): Future[Unit] = queueSink.pull() flatMap {
      case Some(message) => access
        .transition(message :: _)
        .flatMap(_ => aux())
      case None =>
        Future.unit
    }
    aux()
    Extension.Handlers[Future, List[String], String](
      onMessage = message => queue.offer(message).map(_ => ()),
      onDestroy = () => Future.successful(queueSink.cancel())
    )
  }

  private val el = elementId()

  private val config = KorolevServiceConfig[Future, List[String], String](
    stateLoader = StateLoader.default(Nil),
    extensions = List(topicListener),
    render = { xs =>
      optimize {
        body(
          div(
            xs map { x =>
              div(x)
            }
          ),
          input(`type` := "text", el),
          button("Click me!",
            event("click") { access =>
              for (s <- access.valueOf(el); _ <- access.publish(s))
                yield ()
            }
          )
        )
      }
    }
  )

  private val route = akkaHttpService(config).apply(AkkaHttpServerConfig())

  Http().bindAndHandle(route, "0.0.0.0", 8080)
}
