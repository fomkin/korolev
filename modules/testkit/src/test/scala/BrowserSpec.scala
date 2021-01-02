import korolev.Context
import korolev.Context.ElementId
import korolev.testkit.{Action, Browser}
import korolev.state.javaSerialization._

import org.scalatest.{AsyncFlatSpec, Matchers}
import scala.concurrent.Future

class BrowserSpec extends AsyncFlatSpec with Matchers {

  "Browser().access" should "emulate transitions" in {
    Browser()
      .access[Future, String, Any]("hello", _.transition(_ + " world"))
      .map { actions =>
        actions shouldEqual List(Action.Transition("hello world"))
      }
  }

  it should "emulate property set" in {
    val e1 = new ElementId(Some("e1"))
    Browser()
      .property(e1, "value", "hello")
      .access[Future, String, Any]("", access =>
        access.valueOf(e1).flatMap { v =>
          access.property(e1).set("value", s"$v world")
        }
      )
      .map { actions =>
        actions shouldEqual List(Action.PropertySet(e1, "value", "hello world"))
      }
  }

  it should "emulate focus" in {
    val e1 = new ElementId(Some("e1"))
    Browser()
      .access[Future, String, Any]("", _.focus(e1))
      .map { actions =>
        actions shouldEqual List(Action.Focus(e1))
      }
  }

  it should "emulate reset form" in {
    val e1 = new ElementId(Some("e1"))
    Browser()
      .access[Future, String, Any]("", _.resetForm(e1))
      .map { actions =>
        actions shouldEqual List(Action.ResetForm(e1))
      }
  }

  it should "emulate message publishing" in {
    Browser()
      .access[Future, String, Any]("", _.publish("hello world"))
      .map { actions =>
        actions shouldEqual List(Action.Publish("hello world"))
      }
  }

  it should "emulate evalJs" in {
    Browser()
      .mockJs("function foo() { return 1 }")
      .mockJs("function bar() { return Promise.resolve(2) }")
      .access[Future, String, Any]("", access =>
        for {
          foo <- access.evalJs("foo()")
          bar <- access.evalJs("bar()")
          _ <- access.publish(s"$foo $bar")
        } yield ()
      )
      .map { actions =>
        actions shouldEqual List(
          Action.EvalJs(Right("1")),
          Action.EvalJs(Right("2")),
          Action.Publish("1 2")
        )
      }
  }

  "Browser.event" should "emulate event propagation" in {

    val context = Context[Future, String, Any]

    import context._
    import levsha.dsl._
    import html._

    def onClick(access: Access) =
      access.publish("hello world")

    val dom = body(
      div("Hello world"),
      button(
        event("click")(onClick),
        name := "my-button",
        "Click me"
      )
    )

    Browser()
      .event(
        state = "",
        dom = dom,
        event = "click",
        target = _.byName("my-button").headOption.map(_.id),
      )
      .map { actions =>
        actions shouldEqual List(Action.Publish("hello world"))
      }
  }
}
