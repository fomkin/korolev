package korolev

import scala.concurrent.Future
import scala.reflect.ClassTag

trait BrowserEffects[S] {

  import BrowserEffects._
  import EventPhase._

  def elementId = new ElementId()

  def event(name: Symbol, phase: EventPhase = Bubbling)(
      effect: => EventResult[S]): SimpleEvent[S] =
    SimpleEvent(name, phase, () => effect)

  def eventWithAccess(name: Symbol, phase: EventPhase = Bubbling)(
      effect: BrowserAccess => EventResult[S]): EventWithAccess[S] =
    EventWithAccess(name, phase, effect)
}

object BrowserEffects {

  trait BrowserAccess {
    def property[T](id: ElementId, propName: Symbol): Future[T]
  }

  sealed trait Event[S] extends VDom.Misc {
    def name: Symbol
    def phase: EventPhase
  }

  case class EventWithAccess[S](
      name: Symbol,
      phase: EventPhase,
      effect: BrowserAccess => EventResult[S])
      extends Event[S]

  case class SimpleEvent[S](name: Symbol,
                                      phase: EventPhase,
                                      effect: () => EventResult[S])
    extends Event[S]

  class ElementId extends VDom.Misc

}
