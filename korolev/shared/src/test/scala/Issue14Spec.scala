import bridge.JSAccess
import korolev.Effects.Event
import korolev._
import org.scalatest.{FlatSpec, Matchers}
import korolev.Async.Promise

import scala.collection.mutable
import scala.concurrent.Future
import korolev.testExecution._

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
class Issue14Spec extends FlatSpec with Matchers {

  val ba = Effects[Future, Issue14Spec.S, Any]
  import ba._

  "Korolev" should "ignore events from outdated DOM" in {

    var counter = 0

    val jSAccess = new JSAccess {
      def send(args: Seq[Any]): Unit = {}
      protected val promises = mutable.Map.empty[Int, Promise[Future, Any]]
      protected val callbacks = mutable.Map.empty[String, (Any) => Unit]
      implicit val executionContext = korolev.testExecution.defaultExecutor
    }

    Korolev(
      ja = jSAccess,
      sm = StateManager[Future, Issue14Spec.S]("firstState"),
      initialState = "firstState",
      fromScratch = true,
      router = Router.empty[Future, String, String],
      messageHandler = PartialFunction.empty,
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
    jSAccess.resolvePromise(3, isSuccess = true, "@obj:^cb2") // FormData progress handler
    jSAccess.resolvePromise(4, isSuccess = true, "@unit")
    jSAccess.resolvePromise(5, isSuccess = true, "@unit")
    jSAccess.resolvePromise(6, isSuccess = true, "@unit")
    jSAccess.resolvePromise(7, isSuccess = true, "@unit")
    jSAccess.resolvePromise(8, isSuccess = true, "@unit")

    jSAccess.fireCallback("^cb1", "1:0_1_0:mousedown")
    jSAccess.fireCallback("^cb1", "1:0_1_0:mouseup")
    jSAccess.fireCallback("^cb1", "1:0_1_0:click")

    counter should be (1)
  }
}

object Issue14Spec {

  type S = String

  def render(firstEvent: Event[Future, S, Any], secondEvent: Event[Future, S, Any]): Render[S] = {
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
