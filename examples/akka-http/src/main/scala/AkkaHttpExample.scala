import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, Materializer}
import korolev._
import korolev.akkahttp._
import korolev.execution._
import korolev.server._
import korolev.state.javaSerialization._

import scala.concurrent.Future

object AkkaHttpExample extends App {

  private implicit val actorSystem: ActorSystem = ActorSystem()
  private implicit val materializer: Materializer = ActorMaterializer()

  val applicationContext = Context[Future, Boolean, Any]

  import applicationContext._
  import levsha.dsl._
  import html.{body, button}

  private val config = KorolevServiceConfig[Future, Boolean, Any](
    stateStorage = StateStorage.default(false),
    router = Router.empty,
    render = { case s =>
      optimize {
        body(
          s"Hello akka-http: $s",
          button("Click me!",
            event("click")(_.transition(!_))
          )
        )
      }
    }
  )

  private val route = akkaHttpService(config).apply(AkkaHttpServerConfig())

  Http().bindAndHandle(route, "0.0.0.0", 8080)
}
