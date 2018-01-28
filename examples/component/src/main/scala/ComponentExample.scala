import korolev._
import korolev.server._
import korolev.blazeServer._
import korolev.execution._
import korolev.state.javaSerialization._

import scala.concurrent.Future
import scala.util.Random

object ComponentExample extends KorolevBlazeServer {

  import State.globalContext._
  import symbolDsl._

  type Rgb = (Int, Int, Int)
  val Black = (0, 0, 0)
  val Red = (255, 0, 0)

  def randomRgb() = (Random.nextInt(255), Random.nextInt(255), Random.nextInt(255))

  // Declare component as a function syntax
  val ComponentAsFunction = Component[Future, Rgb, String, Unit](Black) { (context, label, state) =>

    import context._
    import symbolDsl._

    val (r, g, b) = state

    'div(
      'borderWidth @= 2,
      'borderStyle @= "solid",
      'borderColor @= s"rgb($r, $g, $b)",
      label,
      event('click) { access =>
        access.transition(_ => randomRgb()) flatMap { _ =>
          access.publish(())
        }
      }
    )
  }

  // Declare component as an object syntax
  object ComponentAsObject extends Component[Future, Rgb, String, Unit](Black) {

    import context._
    import symbolDsl._

    def render(label: String, state: (Int, Int, Int)): Node = {
      val (r, g, b) = state
      'div(
        'borderWidth @= 2,
        'borderStyle @= "solid",
        'borderColor @= s"rgb($r, $g, $b)",
        label,
        event('click) { access =>
          access.publish(()).flatMap { _ =>
            access.transition {
              case _ => randomRgb()
            }
          }
        }
      )
    }
  }

  val service = blazeService[Future, String, Any] from KorolevServiceConfig[Future, String, Any] (
    router = emptyRouter,
    stateStorage = StateStorage.default("a"),
    render = {
      case state =>
        'body(
          s"State is $state",
          ComponentAsObject("Click me, i'm function") { (access, _) =>
            access.transition(_ + Random.nextPrintableChar())
          },
          ComponentAsFunction("Click me, i'm object") { (access, _) =>
            access.transition(_ + Random.nextPrintableChar())
          },
          'button(
            "Click me too",
            event('click) { access =>
              access.transition(_ + Random.nextPrintableChar())
            }
          )
        )
    }
  )
}

object State {
  val globalContext = Context[Future, String, Any]
}

