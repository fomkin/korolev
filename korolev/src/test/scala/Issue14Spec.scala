import korolev._
import org.scalatest.{FlatSpec, Matchers}
import korolev.internal.{ApplicationInstance, Connection}
import korolev.state.StateStorage
import korolev.state.javaSerialization._

import scala.concurrent.Future
import korolev.testExecution._

class Issue14Spec extends FlatSpec with Matchers {

  import Issue14Spec.context._

  "Korolev" should "ignore events from outdated DOM" in {

    var counter = 0

    val connection = new Connection[Future]()

    new ApplicationInstance(
      sessionId = QualifiedSessionId("", ""),
      connection = connection,
      fromScratch = true,
      router = Router.empty[Future, String],
      render = {
        Issue14Spec.render(
          firstEvent = event('mousedown) { access =>
            access.transition { _ =>
              counter += 1
              "secondState"
            }
          },
          secondEvent = event('click) { access =>
            access.transition { _ =>
              counter += 1
              "firstState"
            }
          }
        )
      },
      stateManager = StateStorage.default("firstState").get("", "").value.get.get,
      initialState = "firstState",
      initialPath = "/",
      reporter = Reporter.PrintReporter
    )

    def fireEvent(data: String): Unit =
      connection.receive(s"""[0,"$data"]""")

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
