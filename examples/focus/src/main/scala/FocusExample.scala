import korolev._
import korolev.blazeServer._
import korolev.execution._
import korolev.server._

import scala.concurrent.Future

object FocusExample extends KorolevBlazeServer {

  val applicationContext = ApplicationContext[Future, Boolean, Any]

  import applicationContext._
  import symbolDsl._

  // Handler to input
  val inputId = elementId

  val service = blazeService[Future, Boolean, Any] from KorolevServiceConfig[Future, Boolean, Any](
    stateStorage = StateStorage.default(false),
    serverRouter = ServerRouter.empty[Future, Boolean],
    render = {
      case _ =>
        'body(
          'div("Focus example"),
          'div(
            'input(
              inputId,
              'type /= "text",
              'placeholder /= "Wanna get some focus?"
            )
          ),
          'div(
            'button(
              eventWithAccess('click) { access =>
                deferredTransition {
                  access.focus(inputId).map(_ => emptyTransition)
                }
              },
              "Click to focus"
            )
          )
        )
    }
  )
}
