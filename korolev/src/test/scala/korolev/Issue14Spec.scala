package korolev

import korolev.effect.{Queue, Reporter, Scheduler}
import korolev.internal.{ApplicationInstance, Frontend}
import korolev.state.StateStorage
import korolev.state.javaSerialization._
import korolev.testExecution._
import levsha.{Id, StatefulRenderContext, XmlNs}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Future

class Issue14Spec extends FlatSpec with Matchers {

  import Issue14Spec.context._
  import Reporter.PrintReporter.Implicit

  "Korolev" should "ignore events from outdated DOM" in {

    var counter = 0

    val incomingMessages = Queue[Future, String]()
    val frontend = new Frontend[Future](incomingMessages.stream)
    val app = new ApplicationInstance[Future, Issue14Spec.S, Any](
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
      scheduler = new Scheduler[Future](),
      createMiscProxy = (rc, k) => new StatefulRenderContext[Context.Binding[Future, Issue14Spec.S, Any]] { proxy =>
        def currentContainerId: Id = rc.currentContainerId
        def currentId: Id = rc.currentId
        def subsequentId: Id = rc.subsequentId
        def openNode(xmlns: XmlNs, name: String): Unit = rc.openNode(xmlns, name)
        def closeNode(name: String): Unit = rc.closeNode(name)
        def setAttr(xmlNs: XmlNs, name: String, value: String): Unit = rc.setAttr(xmlNs, name, value)
        def setStyle(name: String, value: String): Unit = rc.setStyle(name, value)
        def addTextNode(text: String): Unit = rc.addTextNode(text)
        def addMisc(misc: Context.Binding[Future, Issue14Spec.S, Any]): Unit = k(rc, misc)
      }
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
  import levsha.dsl._
  import html._

  def render(firstEvent: Event, secondEvent: Event): Render = {
    case "firstState" =>
      body(
        div("Hello"),
        div(
          button("Click me", firstEvent)
        )
      )
    case "secondState" =>
      body(
        div("Hello"),
        ul(
          li("One", secondEvent),
          li("Two"),
          li("Three")
        ),
        div("Cow")
      )
  }
}
