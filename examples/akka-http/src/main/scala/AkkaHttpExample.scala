import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, Materializer}
import korolev._
import korolev.akka._
import korolev.server._
import korolev.state.javaSerialization._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object AkkaHttpExample extends App {

  private implicit val actorSystem: ActorSystem = ActorSystem()
  private implicit val materializer: Materializer = ActorMaterializer()

  val applicationContext = Context[Future, Boolean, Any]

  import applicationContext._
  import levsha.dsl._
  import html.{body, button}

  private val config = KorolevServiceConfig[Future, Boolean, Any](
    stateLoader = StateLoader.default(false),
    render = { s: Boolean =>
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
