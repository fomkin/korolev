package korolev

import scala.language.higherKinds

class Effects[F[+_]: Async, S, M] {

  import Effects._
  import EventPhase._

  type Event = Effects.Event[F, S, M]
  type EventFactory[T] = T => Event
  type Transition = Dux.Transition[S]

  def elementId = new ElementId()

  def event(name: Symbol, phase: EventPhase = Bubbling)(
      effect: => EventResult[F, S]): SimpleEvent[F, S, M] =
    SimpleEvent[F, S, M](name, phase, () => effect)

  def eventWithAccess(name: Symbol, phase: EventPhase = Bubbling)(
      effect: Access[F, M] => EventResult[F, S]): EventWithAccess[F, S, M] =
    EventWithAccess(name, phase, effect)

  def immediateTransition(transition: Dux.Transition[S]): EventResult[F, S] =
    EventResult[F, S](Some(transition), None, sp = false)

  def deferredTransition(transition: F[Dux.Transition[S]]): EventResult[F, S] =
    EventResult[F, S](None, Some(transition), sp = false)

  /**
    * This is an immediateTransition return same state
    */
  def noTransition: EventResult[F, S] = immediateTransition {
    case anyState => anyState
  }

  def transition(t: Transition): Transition = t
}

object Effects {

  /**
    * @tparam F Monad
    * @tparam S State
    * @tparam M Message
    */
  def apply[F[+_]: Async, S, M] = new Effects[F, S, M]()

  abstract class Access[F[+_]: Async, M] {
    def property[T](id: ElementId, propName: Symbol): F[T]
    def publish(message: M): F[Unit]
  }

  sealed abstract class Event[F[+_]: Async, S, M] extends VDom.Misc {
    def `type`: Symbol
    def phase: EventPhase
  }

  case class EventWithAccess[F[+_]: Async, S, M](
      `type`: Symbol,
      phase: EventPhase,
      effect: Access[F, M] => EventResult[F, S])
      extends Event[F, S, M]

  case class SimpleEvent[F[+_]: Async, S, M](`type`: Symbol,
                                      phase: EventPhase,
                                      effect: () => EventResult[F, S])
    extends Event[F, S, M]

  class ElementId extends VDom.Misc

}
