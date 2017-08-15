import korolev._
import korolev.server._
import korolev.blazeServer._
import korolev.execution._

import scala.concurrent.Future
import scala.util.Random

object ComponentExample extends KorolevBlazeServer {

  import State.applicationContext._
  import symbolDsl._

  def randomRgb() = (Random.nextInt(255), Random.nextInt(255), Random.nextInt(255))

  val myComponent = Component[Future, (Int, Int, Int), Unit] { (context, state) =>
    import context._
    import symbolDsl._
    val (r, g, b) = state
    'div(
      'style /= s"border: 2px solid rgb($r, $g, $b)",
      "Click me!",
      eventWithAccess('click) { access =>
        deferredTransition {
          access.publish(()).map { _ =>
            transition {
              case _ => randomRgb()
            }
          }
        }
      }
    )
  }

  val service = blazeService[Future, Int, Any] from KorolevServiceConfig[Future, Int, Any] (
    serverRouter = ServerRouter.empty[Future, Int],
    stateStorage = StateStorage.default(0),
    render = {
      case state =>
        'body(
          s"Button clicked $state times",
          myComponent[Int, Any]((0, 0, 0)) { _ =>
            immediateTransition {
              case n => n + 1
            }
          }
        )
    }
  )
}

object State {
  val applicationContext = ApplicationContext[Future, Int, Any]
}

