import korolev.testExecution._
import korolev.Effects.{Access, ElementId, FormDataDownloader}
import korolev.VDom.Id
import korolev._
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.mutable
import scala.concurrent.Future

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
class EventPropagationSpec
    extends FlatSpec
    with Matchers
    with EventPropagation
    with EventTesting {

  import EventPhase._
  import korolev.Async._

  "propagateEvent" should "fire events climb the hierarchy" in {
    assertEvent { implicit context =>
      val events = Map(
        assertFired("0_0", tpe = "test", inPhase = Capturing),
        assertFired("0_0_0", tpe = "test", inPhase = Capturing),
        assertNotFired("0_0_0", tpe = "anotherType", inPhase = Bubbling),
        assertNotFired("0_0_1", tpe = "test", inPhase = Bubbling)
      )
      propagateEvent(events, duxApply, BA, Id("0_0_0_2"), "test")
    }
  }

  it should "fire events lowering the hierarchy" in {
    assertEvent { implicit context =>
      val events = Map(
        assertFired("0_0", tpe = "test", inPhase = Bubbling),
        assertFired("0_0_0", tpe = "test", inPhase = Bubbling),
        assertFired("0_0_0_2", tpe = "test", inPhase = Bubbling),
        assertNotFired("0_0_0", tpe = "anotherType", inPhase = Bubbling),
        assertNotFired("0_0_1", tpe = "test", inPhase = Bubbling)
      )
      propagateEvent(events, duxApply, BA, Id("0_0_0_2"), "test")
    }
  }

  it should "fire event at target" in {
    assertEvent { implicit context =>
      val events = Map(
        assertFired("0_0_0_2", tpe = "test", inPhase = AtTarget),
        assertNotFired("0_0_0_2", tpe = "test", inPhase = Capturing),
        assertNotFired("0_0_0_2", tpe = "test", inPhase = Bubbling)
      )
      propagateEvent(events, duxApply, BA, Id("0_0_0_2"), "test")
    }
  }

  it should "stop propagation if handler in capturing returns false" in {
    assertEvent { implicit context =>
      val events = Map(
        assertFired("0_0", tpe = "test", inPhase = Capturing),
        assertFired("0_0_0", tpe = "test", inPhase = Capturing, stop = true),
        assertNotFired("0_0_0_2", tpe = "test", inPhase = AtTarget),
        assertNotFired("0_0_0", tpe = "test", inPhase = Bubbling)
      )
      propagateEvent(events, duxApply, BA, Id("0_0_0_2"), "test")
    }
  }

  it should "stop propagation if handler in at target returns false" in {
    assertEvent { implicit context =>
      val events = Map(
        assertFired("0_0", tpe = "test", inPhase = Capturing),
        assertFired("0_0_0", tpe = "test", inPhase = Capturing),
        assertFired("0_0_0_2", tpe = "test", inPhase = AtTarget, stop = true),
        assertNotFired("0_0_0", tpe = "test", inPhase = Bubbling)
      )
      propagateEvent(events, duxApply, BA, Id("0_0_0_2"), "test")
    }
  }


}

trait EventTesting extends FlatSpec {

  type S = String
  type R = (String, Effects.Event[Future, S, Any])

  type ShouldFire = Boolean
  type Comment = String
  type EventAssertion = (ShouldFire, Comment)
  type Context = mutable.Map[String, EventAssertion]

  def duxApply(implicit context: Context): StateManager.Transition[S] => Future[Unit] = { t =>
    val key = t("")
    context(key) match {
      case (false, comment) => fail(comment)
      case (true, _) => context.remove(key)
    }
    Future.successful(())
  }

  val BE = Effects[Future, String, Any]

  object BA extends Access[Future, String, Any] {

    def publish(message: Any): Future[Unit] = Future.successful(())

    def property[T](id: ElementId, propName: Symbol): Future[T] =
      Future.failed(new Exception())

    def downloadFormData(id: ElementId): FormDataDownloader[Future, String] = ???
  }

  def assertEvent[U](fillContext: Context => U): Unit = {
    val context: Context = mutable.Map.empty
    fillContext(context)
    context foreach {
      case (_, (true, comment)) => fail(comment)
      case _ =>
    }
  }

  def assertNotFired(id: String, tpe: String, inPhase: EventPhase)
    (implicit context: Context): R = {
    val key = s"$id:$tpe:$inPhase"
    context.put(key, (false, s"Event $key should not be fired in $inPhase"))
    key -> Effects.SimpleEvent(Symbol(tpe), inPhase, () =>
      BE.immediateTransition { case _ => key })
  }

  def assertFired(id: String,
                  tpe: String,
                  inPhase: EventPhase,
                  stop: Boolean = false)(implicit context: Context): R = {
    val key = s"$id:$tpe:$inPhase"
    val f = { () =>
      val t = BE.immediateTransition { case _ => key }
      if (stop) t.stopPropagation else t
    }
    context.put(key, (true, s"Event $key should be fired in $inPhase"))
    key -> Effects.SimpleEvent(Symbol(tpe), inPhase, f)
  }

}
