import korolev._
import korolev.server._
import korolev.blazeServer._
import korolev.execution._

import scala.concurrent.Future
import scala.concurrent.duration._

object DelayExample extends KorolevBlazeServer {

  val globalContext = Context[Future, Option[Int], Any]

  import globalContext._
  import symbolDsl._

  val service = blazeService[Future, Option[Int], Any] from KorolevServiceConfig[Future, Option[Int], Any](
    stateStorage = StateStorage.default(Option.empty[Int]),
    serverRouter = ServerRouter.empty[Future, Option[Int]],
    render = {
      case Some(n) => 'body(
        delay(3.seconds) { access =>
          access.transition {
            case _ => None
          }
        },
        'button(
          "Push the button " + n,
          event('click) { access =>
            access.transition {
              case s => s.map(_ + 1)
            }
          }
        ),
        "Wait 3 seconds!"
      )
      case None => 'body(
        'button(
          event('click) { access =>
            access.transition {
              case _ => Some(1)
            }
          },
          "Push the button"
        )
      )
    }
  )
}

