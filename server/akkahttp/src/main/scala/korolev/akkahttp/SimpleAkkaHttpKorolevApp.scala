package korolev.akkahttp

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, Materializer}
import korolev.execution._

abstract class SimpleAkkaHttpKorolevApp(config: AkkaHttpServerConfig = null) {

  implicit val actorSystem: ActorSystem = ActorSystem()
  implicit val materializer: Materializer = ActorMaterializer()

  def service: AkkaHttpService

  def main(args: Array[String]): Unit = {
    val escapedConfig =
      if (config == null) AkkaHttpServerConfig()
      else config
    val route = service(escapedConfig)
    Http().bindAndHandle(route, "0.0.0.0", 8080)
    ()
  }
}
