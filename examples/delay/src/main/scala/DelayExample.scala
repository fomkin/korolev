import korolev._
import korolev.akkahttp._
import korolev.execution._
import korolev.server._
import korolev.state.javaSerialization._

import scala.concurrent.Future
import scala.concurrent.duration._

object DelayExample extends SimpleAkkaHttpKorolevApp {

  val globalContext = Context[Future, Option[Int], Any]

  import globalContext._
  import levsha.dsl._
  import html._

  val service = akkaHttpService {
    KorolevServiceConfig[Future, Option[Int], Any](
      stateLoader = StateLoader.default(Option.empty[Int]),
      render = {
        case Some(n) =>
          optimize {
            body(
              delay(3.seconds) { access =>
                access.transition {
                  case _ => None
                }
              },
              button(
                "Push the button " + n,
                event("click") { access =>
                  access.transition {
                    case s => s.map(_ + 1)
                  }
                }
              ),
              "Wait 3 seconds!"
            )
          }
        case None => optimize {
          body(
            button(
              event("click") { access =>
                access.transition {
                  case _ => Some(1)
                }
              },
              "Push the button"
            )
          )
        }
      }
    )
  }
}

