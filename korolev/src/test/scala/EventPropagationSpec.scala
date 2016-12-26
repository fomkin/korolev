import korolev.BrowserEffects.{BrowserAccess, ElementId}
import korolev.VDom.Id
import korolev._
import org.scalatest.{FlatSpec, Matchers}
import RunNowExecutionContext.instance

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

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
  type R = (String, BrowserEffects.Event[Future, S])

  type ShouldFire = Boolean
  type Comment = String
  type EventAssertion = (ShouldFire, Comment)
  type Context = mutable.Map[String, EventAssertion]

  def duxApply(implicit context: Context): Dux.Transition[S] => Future[Unit] = { t =>
    val key = t("")
    context(key) match {
      case (false, comment) => fail(comment)
      case (true, _) => context.remove(key)
    }
    Future.successful(())
  }

  object BA extends BrowserAccess[Future] {
    def property[T](id: ElementId, propName: Symbol): Future[T] =
      Future.failed(new Exception())
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
    key -> BrowserEffects.SimpleEvent(Symbol(tpe), inPhase, () =>
      EventResult.immediateTransition[Future, S] { case _ => key })
  }

  def assertFired(id: String,
                  tpe: String,
                  inPhase: EventPhase,
                  stop: Boolean = false)(implicit context: Context): R = {
    val key = s"$id:$tpe:$inPhase"
    val f = { () =>
      val t = EventResult.immediateTransition[Future, S] { case _ => key }
      if (stop) t.stopPropagation else t
    }
    context.put(key, (true, s"Event $key should be fired in $inPhase"))
    key -> BrowserEffects.SimpleEvent(Symbol(tpe), inPhase, f)
  }

}
