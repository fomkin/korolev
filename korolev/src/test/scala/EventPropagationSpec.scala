import korolev.VDom.Id
import korolev.{Event, EventPropagation}
import org.scalatest.{FlatSpec, Matchers}

import scala.collection.mutable

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
class EventPropagationSpec
    extends FlatSpec
    with Matchers
    with EventPropagation
    with EventTesting {

  import Event._

  "propagateEvent" should "fire events climb the hierarchy" in {
    assertEvent { implicit context =>
      val events = Map(
        assertFired("0_0", tpe = "test", inPhase = Capturing),
        assertFired("0_0_0", tpe = "test", inPhase = Capturing),
        assertNotFired("0_0_0", tpe = "anotherType"),
        assertNotFired("0_0_1", tpe = "test")
      )

      propagateEvent(events, Id("0_0_0_2"), "test")
    }
  }

  it should "fire events lowering the hierarchy" in {
    assertEvent { implicit context =>
      val events = Map(
        assertFired("0_0", tpe = "test", inPhase = Bubbling),
        assertFired("0_0_0", tpe = "test", inPhase = Bubbling),
        assertNotFired("0_0_0", tpe = "anotherType"),
        assertNotFired("0_0_1", tpe = "test")
      )
      propagateEvent(events, Id("0_0_0_2"), "test")
    }
  }

  it should "fire event at target" in {
    assertEvent { implicit context =>
      val events = Map(
        assertFired("0_0_0_2", tpe = "test", inPhase = AtTarget),
        assertNotFired("0_0_0_2", tpe = "test", inPhase = Capturing),
        assertNotFired("0_0_0_2", tpe = "test", inPhase = Bubbling)
      )
      propagateEvent(events, Id("0_0_0_2"), "test")
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
      propagateEvent(events, Id("0_0_0_2"), "test")
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
      propagateEvent(events, Id("0_0_0_2"), "test")
    }
  }


}

trait EventTesting extends FlatSpec {

  import Event.Phase

  type Context = mutable.Map[String, Boolean]

  def assertEvent[U](fillContext: Context => U): Unit = {
    val context: Context = mutable.Map.empty
    fillContext(context)
    context foreach {
      case (s, false) =>
        val Array(_, _, phase) = s.split(':')
        fail(s"Event $s should fired in $phase")
      case _ =>
    }
  }

  def assertNotFired(id: String, tpe: String, inPhase: Phase = null) = {
    val pair = s"$id:$tpe:$inPhase"
    pair -> new Event {
      def payload: Any = ()
      def fire(): Boolean = {
        Option(inPhase) match {
          case None => fail(s"Event $pair should not be fired")
          case Some(`inPhase`) =>
            fail(s"Event $pair should not be fired in $inPhase")
          case _ => true
        }
      }
      val phase = inPhase
      def `type`: String = tpe
    }
  }

  def assertFired(id: String,
                  tpe: String,
                  inPhase: Phase,
                  stop: Boolean = false)(implicit context: Context) = {
    val key = s"$id:$tpe:$inPhase"
    context.put(key, false)
    key -> new Event {
      def payload: Any = ()
      def fire(): Boolean = {
        context.put(key, true)
        !stop
      }
      val phase = inPhase
      def `type`: String = tpe
    }

  }

}
