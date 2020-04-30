package korolev

import korolev.effect.{Queue, Reporter, Scheduler}
import korolev.internal.{ApplicationInstance, Frontend}
import korolev.state.StateStorage
import korolev.state.javaSerialization._
import korolev.testExecution._
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Future

class Issue14Spec extends FlatSpec with Matchers {

  import Issue14Spec.context._
  import Reporter.PrintReporter.Implicit

  "Korolev" should "ignore events from outdated DOM" in {

    var counter = 0

    val incomingMessages = Queue[Future, String]()
    val frontend = new Frontend[Future](incomingMessages.stream)
    val app = new ApplicationInstance(
      sessionId = Qsid("", ""),
      frontend = frontend,
      router = Router.empty[Future, String],
      render = {
        Issue14Spec.render(
          firstEvent = event("mousedown") { access =>
            access.transition { _ =>
              counter += 1
              "secondState"
            }
          },
          secondEvent = event("click") { access =>
            access.transition { _ =>
              counter += 1
              "firstState"
            }
          }
        )
      },
      stateManager = new StateStorage.SimpleInMemoryStateManager[Future](),
      initialState = "firstState",
      reporter = Reporter.PrintReporter,
      scheduler = new Scheduler[Future]()
    )

    def fireEvent(data: String) =
      incomingMessages.offerUnsafe(s"""[0,"$data"]""")

    app.initialize(true)
    fireEvent("1:1_2_1:mousedown")
    fireEvent("1:1_2_1:mouseup")
    fireEvent("1:1_2_1:click")

    counter should be (1)
  }
}

object Issue14Spec {

  type S = String

  val context = Context[Future, Issue14Spec.S, Any]

  import context._
  import context.symbolDsl._

  def render(firstEvent: Event, secondEvent: Event): Render = {
    case "firstState" =>
      'body(
        'div("Hello"),
        'div(
          'button("Click me", firstEvent)
        )
      )
    case "secondState" =>
      'body(
        'div("Hello"),
        'ul(
          'li("One", secondEvent),
          'li("Two"),
          'li("Three")
        ),
        'div("Cow")
      )
  }
}
