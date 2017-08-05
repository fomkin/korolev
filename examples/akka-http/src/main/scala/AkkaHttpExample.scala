import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import korolev._
import korolev.akkahttp._
import korolev.execution._
import korolev.server._

import scala.concurrent.Future

object AkkaHttpExample extends App {

  val applicationContext = ApplicationContext[Future, Boolean, Any]

  import applicationContext._
  import symbolDsl._

  private val config = KorolevServiceConfig[Future, Boolean, Any](
    stateStorage = StateStorage.default(false),
    serverRouter = ServerRouter.empty[Future, Boolean],
    render = { case _ => 'div("Hello akka-http") }
  )

  private implicit val actorSystem = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  private val route = akkaHttpService(config).apply(AkkaHttpServerConfig())

  Http().bindAndHandle(route, "0.0.0.0", 8080)
}
