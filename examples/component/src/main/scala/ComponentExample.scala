import ComponentExample.ComponentWithStateLoader
import korolev.*
import korolev.akka.*
import korolev.effect.Scheduler
import korolev.effect.syntax.*
import korolev.server.*
import korolev.state.javaSerialization.*

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.util.Random

object ComponentExample extends SimpleAkkaHttpKorolevApp {

  import State.globalContext._

  import levsha.dsl._
  import html._

  type Rgb = (Int, Int, Int)
  val Black = (0, 0, 0)
  val Red = (255, 0, 0)

  def randomRgb() = (Random.nextInt(255), Random.nextInt(255), Random.nextInt(255))

  // Declare component as a function syntax
  val ComponentAsFunction = Component[Future, Rgb, String, Unit](Black) { (context, label, state) =>

    import context._

    val (r, g, b) = state
    optimize {
      div(
        borderWidth @= "2px",
        borderStyle @= "solid",
        borderColor @= s"rgb($r, $g, $b)",
        label,
        event("click") { access =>
          access.transition(_ => randomRgb()) flatMap { _ =>
            access.publish(())
          }
        }
      )
    }
  }

  // Declare component as an object syntax
  object ComponentAsObject extends Component[Future, Rgb, String, Unit](Black) {

    import context._

    def render(label: String, state: (Int, Int, Int)): Node = {
      val (r, g, b) = state
      div(
        borderWidth @= "2px",
        borderStyle @= "solid",
        borderColor @= s"rgb($r, $g, $b)",
        label,
        event("click") { access =>
          access.publish(()).flatMap { _ =>
            access.transition {
              case _ => randomRgb()
            }
          }
        }
      )
    }
  }

  object ComponentWithStateLoader extends Component[Future, String, Int, Any](
    loadState = (params: Int) =>
      Scheduler[Future].sleep(1000.millis).as(params.toString)
  ) {
    def render(parameters: Int, state: String): context.Node = {
      div(s"Render with state, State is ${state}")
    }

    override def renderNoState(parameters: Int): context.Node = {
      div(s"Render without state")
    }
  }

  val service: AkkaHttpService = akkaHttpService {
    KorolevServiceConfig[Future, String, Any] (
      stateLoader = StateLoader.default("a"),
      document = { state =>
        Html(
          body(
            s"State is $state",
            ComponentAsObject("Click me, i'm function") { (access, _) =>
              access.transition(_ + Random.nextPrintableChar())
            },
            ComponentAsFunction("Click me, i'm object") { (access, _) =>
              access.transition(_ + Random.nextPrintableChar())
            },
            ComponentWithStateLoader.silent(42),
            button(
              "Click me too",
              event("click") { access =>
                access.transition(_ + Random.nextPrintableChar())
              }
            )
          )
        )
      }
    )
  }
}

object State {
  val globalContext = Context[Future, String, Any]
}

