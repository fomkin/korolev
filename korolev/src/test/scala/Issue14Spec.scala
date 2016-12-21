import bridge.JSAccess
import korolev.BrowserEffects.Event
import korolev.EventResult._
import korolev.{BrowserEffects, Korolev, Shtml}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.ExecutionContext

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
class Issue14Spec extends FlatSpec with Matchers with BrowserEffects[Issue14Spec.S] {

  "Korolev" should "ignore events from outdated DOM" in {

    implicit val ec = new Issue14Spec.RunNowExecutionContext()
    var counter = 0

    val jSAccess = new JSAccess {
      def send(args: Seq[Any]): Unit = {}
      implicit val executionContext = ec
    }

    Korolev(
      jsAccess = jSAccess,
      initialState = "firstState",
      fromScratch = true,
      render = Issue14Spec.render(
        firstEvent = event('mousedown) {
          immediateTransition[String] { case _ =>
            counter += 1
            "secondState"
          }
        },
        secondEvent = event('click) {
          immediateTransition[String] { case _ =>
            counter += 1
            "firstState"
          }
        }
      )
    )

    jSAccess.resolvePromise(0, isSuccess = true, "@obj:@Korolev")
    jSAccess.resolvePromise(1, isSuccess = true, "@obj:^cb0")

    jSAccess.fireCallback("^cb0", "1:0_1_0:mousedown")
    jSAccess.fireCallback("^cb0", "1:0_1_0:mouseup")
    jSAccess.fireCallback("^cb0", "1:0_1_0:click")

    counter should be (1)
  }
}

object Issue14Spec extends Shtml {

  type S = String

  def render(firstEvent: Event[S], secondEvent: Event[S]): Korolev.Render[S] = {
    case "firstState" =>
      'div(
        'div("Hello"),
        'div(
          'button("Click me", firstEvent)
        )
      )
    case "secondState" =>
      'div(
        'div("Hello"),
        'ul(
          'li("One", secondEvent),
          'li("Two"),
          'li("Three")
        ),
        'div("Cow")
      )
  }

  class RunNowExecutionContext extends ExecutionContext {
    def execute(runnable: Runnable): Unit = runnable.run()
    def reportFailure(cause: Throwable): Unit = cause.printStackTrace()
  }
}
