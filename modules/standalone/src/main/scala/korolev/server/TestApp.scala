package korolev.server

import korolev.state.javaSerialization._
import scala.concurrent.Future

object TestApp extends KorolevApp[Future, String, Any](gracefulShutdown = true) {

  import context._
  import levsha.dsl._
  import html._

  final val config = Future.successful {
    KorolevServiceConfig(
      stateLoader = StateLoader.default("Hello world"),
      head = _ => Seq(
        link(rel := "stylesheet", href := "static/main.css")
      ),
      render = state => optimize {
        body(
          state,
          button(
            "Plus one",
            event("click")(_.transition(_ + "1"))
          )
        )
      }
    )
  }
}
