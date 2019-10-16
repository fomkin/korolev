import korolev._
import korolev.akkahttp._
import korolev.execution._
import korolev.server._

import scala.concurrent.Future
import korolev.state.javaSerialization._

object FocusExample extends SimpleAkkaHttpKorolevApp {

  val globalContext = Context[Future, Boolean, Any]

  import globalContext._
  import levsha.dsl._
  import html._

  // Handler to input
  val inputId = elementId()

  val service: AkkaHttpService = akkaHttpService {
    KorolevServiceConfig[Future, Boolean, Any](
      stateStorage = StateStorage.default(false),
      router = Router.empty,
      render = {
        case _ => optimize {
          body(
            div("Focus example"),
            div(
              input(
                inputId,
                `type` := "text",
                placeholder := "Wanna get some focus?"
              )
            ),
            div(
              button(
                event("click") { access =>
                  access.focus(inputId)
                },
                "Click to focus"
              )
            )
          )
        }
      }
    )
  }
}
