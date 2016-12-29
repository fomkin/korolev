package korolev

import scala.language.higherKinds

class BrowserEffects[F[+_]: Async, S] {

  import BrowserEffects._
  import EventPhase._

  def elementId = new ElementId()

  def event(name: Symbol, phase: EventPhase = Bubbling)(
      effect: => EventResult[F, S]): SimpleEvent[F, S] =
    SimpleEvent[F, S](name, phase, () => effect)

  def eventWithAccess(name: Symbol, phase: EventPhase = Bubbling)(
      effect: BrowserAccess[F] => EventResult[F, S]): EventWithAccess[F, S] =
    EventWithAccess(name, phase, effect)
}

object BrowserEffects {

  def apply[F[+_]: Async, S] = new BrowserEffects[F, S]()

  abstract class BrowserAccess[F[+_]: Async] {
    def property[T](id: ElementId, propName: Symbol): F[T]
  }

  sealed abstract class Event[F[+_]: Async, S] extends VDom.Misc {
    def `type`: Symbol
    def phase: EventPhase
  }

  case class EventWithAccess[F[+_]: Async, S](
      `type`: Symbol,
      phase: EventPhase,
      effect: BrowserAccess[F] => EventResult[F, S])
      extends Event[F, S]

  case class SimpleEvent[F[+_]: Async,S](`type`: Symbol,
                                      phase: EventPhase,
                                      effect: () => EventResult[F, S])
    extends Event[F, S]

  class ElementId extends VDom.Misc

}
