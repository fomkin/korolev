import bridge.JSAccess
import korolev._
import org.scalatest.{FlatSpec, Matchers}
import korolev.Async.Promise
import korolev.internal.ApplicationInstance

import scala.collection.mutable
import scala.concurrent.Future
import korolev.testExecution._

class Issue14Spec extends FlatSpec with Matchers {

  import Issue14Spec.applicationContext._

  "Korolev" should "ignore events from outdated DOM" in {

    var counter = 0

    val jSAccess = new JSAccess {
      def send(args: Seq[Any]): Unit = {}
      protected val promises = mutable.Map.empty[Int, Promise[Future, Any]]
      protected val callbacks = mutable.Map.empty[String, (Any) => Unit]
      implicit val executionContext = korolev.testExecution.defaultExecutor
    }

    new ApplicationInstance(
      identifier = "",
      jsAccess = jSAccess,
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

    jSAccess.resolvePromise(0, isSuccess = true, "@obj:@Korolev")
    jSAccess.resolvePromise(1, isSuccess = true, "@obj:^cb0") // pop state handler
    jSAccess.resolvePromise(2, isSuccess = true, "@obj:^cb1") // event handler
    jSAccess.resolvePromise(3, isSuccess = true, "@obj:^cb2") // FormData progress handler
    for (i <- 4 to 15) jSAccess.resolvePromise(i, isSuccess = true, "@unit")

    jSAccess.fireCallback("^cb1", "1:1_2_1:mousedown")
    jSAccess.fireCallback("^cb1", "1:1_2_1:mouseup")
    jSAccess.fireCallback("^cb1", "1:1_2_1:click")

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
