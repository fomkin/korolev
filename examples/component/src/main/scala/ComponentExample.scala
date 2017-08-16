import korolev._
import korolev.server._
import korolev.blazeServer._
import korolev.execution._

import scala.concurrent.Future
import scala.util.Random

object ComponentExample extends KorolevBlazeServer {

  import State.applicationContext._
  import symbolDsl._

  type Rgb = (Int, Int, Int)
  val Black = (0, 0, 0)
  val Red = (255, 0, 0)

  def randomRgb() = (Random.nextInt(255), Random.nextInt(255), Random.nextInt(255))

  // Declare component as a function syntax
  val ComponentAsFunction = Component[Future, Rgb, Unit] { (context, state) =>

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

  // Declare component as an object syntax
  object ComponentAsObject extends Component[Future, Rgb, Unit] {

    import context._
    import symbolDsl._

    def render(state: (Int, Int, Int)): Node = {
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
  }

  val service = blazeService[Future, Int, Any] from KorolevServiceConfig[Future, Int, Any] (
    serverRouter = ServerRouter.empty[Future, Int],
    stateStorage = StateStorage.default(0),
    render = {
      case state =>
        'body(
          s"Button clicked $state times",
          ComponentAsObject(Red) { _ =>
            immediateTransition {
              case n => n + 1
            }
          },
          ComponentAsFunction(Black) { _ =>
            immediateTransition {
              case n => n + 1
            }
          },
          'button(
            "Click me too",
            event('click) {
              immediateTransition {
                case n => n + 1
              }
            }
          )
        )
    }
  )
}

object State {
  val applicationContext = ApplicationContext[Future, Int, Any]
}

