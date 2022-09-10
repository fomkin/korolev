import korolev.*
import korolev.akka.*
import korolev.server.*
import korolev.state.javaSerialization.*
import levsha.Document
import levsha.events.EventPhase

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

object ComponentExample extends SimpleAkkaHttpKorolevApp {

  import levsha.dsl._
  import html._

  type Rgb = (Int, Int, Int)
  val Black = (0, 0, 0)
  val Red = (255, 0, 0)

  def randomRgb() = (Random.nextInt(255), Random.nextInt(255), Random.nextInt(255))

//  // Declare component as a function syntax
//  val ComponentAsFunction = Component[Future, Rgb, String, Unit](Black) { (context, label, state) =>
//
//    import context._
//
//    val (r, g, b) = state
//    optimize {
//      div(
//        borderWidth @= "2px",
//        borderStyle @= "solid",
//        borderColor @= s"rgb($r, $g, $b)",
//        label,
//        event("click") { access =>
//          access.transition(_ => randomRgb()) flatMap { _ =>
//            access.publish(())
//          }
//        }
//      )
//    }
//  }

  // Declare component as an object syntax
  object AsyncComponentAsObject extends AsyncComponent[Future, Rgb, String, Unit] {
//    import context._

    override val initialState: (Int, Int, Int) = Black

    override def render(label: String,
                        state: (Int, Int, Int)): Document.Node[Context.Binding[Future, (Int, Int, Int), Unit]] = {
      val (r, g, b) = state
      optimize {
        div(
          div(
            borderWidth @= "2px",
            borderStyle @= "solid",
            borderColor @= s"rgb($r, $g, $b)",
            label,
            Context.Event[Future, Rgb, Unit]("click", EventPhase.Bubbling, false, { access =>
//            access.publish(()).flatMap { _ =>
              access.transition {
                case _ => randomRgb()
              }
//            }
            })
          )
        )
      }
    }

    override def placeholder(label: String,
                             state: (Int, Int, Int)): Document.Node[Context.Binding[Future, (Int, Int, Int), Unit]] = {
      div(
        "Async placeholder",
        Context.Event[Future, Rgb, Unit]("click", EventPhase.Bubbling, false, { access =>
          //            access.publish(()).flatMap { _ =>
          access.evalJs("console.log('click happens!')").map(_ => ())
        })
      )
    }
  }

  // Declare component as an object syntax
  object ComponentAsObject extends Component[Future, Rgb, String, Unit] {
    override val initialState: (Int, Int, Int) = Black
//    import context._

    override def render(label: String,
                        state: (Int, Int, Int)): Document.Node[Context.Binding[Future, (Int, Int, Int), Unit]] = {
      val (r, g, b) = state
      optimize {
        div(
          borderWidth @= "2px",
          borderStyle @= "solid",
          borderColor @= s"rgb($r, $g, $b)",
          label,
          Context.Event[Future, Rgb, Unit]("click", EventPhase.Bubbling, false, { access =>
//            access.publish(()).flatMap { _ =>
            access.transition {
              case _ => randomRgb()
            }
//            }
          })
        )
      }
    }
  }

  import State.globalContext._

  val service: AkkaHttpService = akkaHttpService {
    KorolevServiceConfig[Future, String, Any](
      stateLoader = StateLoader.default("a"),
      document = { state =>
        optimize {
          Html(
            body(
              s"State is $state",
              AsyncComponentAsObject.silent("Async component test"),
              div(),
//              ComponentAsObject("Click me, i'm function") { (access, _) =>
//                access.transition(_ + Random.nextPrintableChar())
//              },
              ComponentAsObject.silent("Click me, i'm function 1"),
              div("test"),
              ComponentAsObject.silent("Click me, i'm function 2"),
              //            ComponentAsFunction("Click me, i'm object") { (access, _) =>
              //              access.transition(_ + Random.nextPrintableChar())
              //            },
//              button(
//                "Click me too",
//                Context.Event[Future, String, Unit]("click", EventPhase.Bubbling, false, { access =>
//                  access.transition(_ + Random.nextPrintableChar())
//                })
//              )
            )
          )
        }
      }
    )
  }
}

object State {
  val globalContext = Context[Future, String, Any]
}
