import korolev._
import org.scalatest.{FlatSpec, Matchers}
import korolev.internal.{ApplicationInstance, Connection}

import scala.concurrent.Future
import korolev.testExecution._

class Issue14Spec extends FlatSpec with Matchers {

  import Issue14Spec.applicationContext._

  "Korolev" should "ignore events from outdated DOM" in {

    var counter = 0

    val connection = new Connection[Future]()

    new ApplicationInstance(
      identifier = "",
      connection = connection,
      fromScratch = true,
      router = Router.empty[Future, String, String],
      render = {
        Issue14Spec.render(
          firstEvent = event('mousedown) {
            immediateTransition { case _ =>
              counter += 1
              "secondState"
            }
          },
          secondEvent = event('click) {
            immediateTransition { case _ =>
              counter += 1
              "firstState"
            }
          }
        )
      },
      stateReader = StateReader.withTopLevelState("firstState")
    )

    def fireEvent(data: String) =
      connection.receive(s"""[0,"$data"]""")

    fireEvent("1:1_2_1:mousedown")
    fireEvent("1:1_2_1:mouseup")
    fireEvent("1:1_2_1:click")

    counter should be (1)
  }
}

object Issue14Spec {

  type S = String

  val applicationContext = ApplicationContext[Future, Issue14Spec.S, Any]

  import applicationContext._
  import applicationContext.symbolDsl._

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
