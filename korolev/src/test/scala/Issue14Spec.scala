import bridge.JSAccess
import korolev.BrowserEffects.Event
import korolev.{BrowserEffects, Korolev, Render, Router, Shtml}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Future

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
class Issue14Spec extends FlatSpec with Matchers {

  val ba = {
    import RunNowExecutionContext.instance
    new BrowserEffects[Future, Issue14Spec.S] {}
  }
  import ba._


  "Korolev" should "ignore events from outdated DOM" in {

    implicit val ec = RunNowExecutionContext.instance
    var counter = 0

    val jSAccess = new JSAccess {
      def send(args: Seq[Any]): Unit = {}
      implicit val executionContext = ec
    }

    Korolev(
      jsAccess = jSAccess,
      initialState = "firstState",
      fromScratch = true,
      router = Router.empty[Future, String, String],
      render = Issue14Spec.render(
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
    )

    jSAccess.resolvePromise(0, isSuccess = true, "@obj:@Korolev")
    jSAccess.resolvePromise(1, isSuccess = true, "@obj:^cb0") // pop state handler
    jSAccess.resolvePromise(2, isSuccess = true, "@obj:^cb1") // event handler

    jSAccess.fireCallback("^cb1", "1:0_1_0:mousedown")
    jSAccess.fireCallback("^cb1", "1:0_1_0:mouseup")
    jSAccess.fireCallback("^cb1", "1:0_1_0:click")

    counter should be (1)
  }
}

object Issue14Spec extends Shtml {

  type S = String

  def render(firstEvent: Event[Future, S], secondEvent: Event[Future, S]): Render[S] = {
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
}
