import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import korolev.testkit._
import zio.Task

class ZioExampleSpec extends AsyncFlatSpec with Matchers {

  import ZioExample._

  private val browser = Browser()
    .value(aInput, "2")
    .value(bInput, "3")

  "onChange" should "read inputs and put calculation result to the view state" in runtime.unsafeRunToFuture {
    browser
      .access[Task, Option[Int], Any](Option.empty[Int], onChange)
      .map { actions =>
        actions shouldEqual List(
          Action.Transition(Some(5))
        )
      }
  }

  it should "be handled" in runtime.unsafeRunToFuture {
    browser.event(Option.empty[Int],
      renderForm(None),
      "input",
      _.byName("a-input").headOption.map(_.id)) map { actions =>
      actions shouldEqual List(
        Action.Transition(Some(5))
      )
    }
  }
}
