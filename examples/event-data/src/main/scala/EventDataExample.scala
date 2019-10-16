import korolev._
import korolev.server._
import korolev.akkahttp._
import korolev.execution._
import korolev.state.javaSerialization._

import scala.concurrent.Future

object EventDataExample extends SimpleAkkaHttpKorolevApp {

  val globalContext = Context[Future, String, Any]

  import globalContext._
  import levsha.dsl._
  import html._

  val service = akkaHttpService {
    KorolevServiceConfig [Future, String, Any] (
      router = Router.empty,
      stateStorage = StateStorage.default("nothing"),
      render = {
        case json => optimize {
          body(
            input(
              `type` := "text",
              event("keydown") { access =>
                access.eventData.flatMap { eventData =>
                  access.transition(_ => eventData)
                }
              }
            ),
            pre(json)
          )
        }
      }
    )
  }
}

