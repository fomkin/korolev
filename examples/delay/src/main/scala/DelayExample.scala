import korolev._
import korolev.server._
import korolev.blazeServer._
import korolev.execution._

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
object DelayExample extends KorolevBlazeServer {

  val applicationContext = ApplicationContext[Future, Boolean, Any]

  import applicationContext._
  import dsl._

  val service = blazeService[Future, Boolean, Any] from KorolevServiceConfig[Future, Boolean, Any](
    stateStorage = StateStorage.default(false),
    serverRouter = ServerRouter.empty[Future, Boolean],
    render = { implicit rc =>
      {
        case true => 'body(
          delay(3.seconds) {
            case true => false
          },
          "Wait 3 seconds!"
        )
        case false => 'body(
          'button(
            event('click) {
              immediateTransition {
                case _ => true
              }
            },
            "Push the button"
          )
        )
      }
    }
  )
}

