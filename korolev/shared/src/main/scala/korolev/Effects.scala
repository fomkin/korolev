package korolev

import korolev.StateManager.Transition
import korolev.util.Scheduler
import korolev.Async.AsyncOps

import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds

class Effects[F[+_]: Async, S, M](implicit scheduler: Scheduler[F]) {

  import Effects._
  import EventPhase._

  type Event = Effects.Event[F, S, M]
  type EventFactory[T] = T => Event
  type Transition = StateManager.Transition[S]

  def elementId = new ElementId()

  /**
    * Schedules the [[transition]] with [[delay]]. For example it can be useful
    * when you want to hide something after timeout.
    */
  def delay(delay: FiniteDuration)(transition: Transition): Delay[F, S] = new Delay[F, S] {

    @volatile var _jobHandler = Option.empty[Scheduler.JobHandler[F, _]]
    @volatile var _finished = false

    def finished: Boolean = _finished

    def cancel(): Unit = _jobHandler.foreach(_.cancel())

    def start(applyTransition: Transition => F[Unit]): Unit = {
      _jobHandler = Some {
        scheduler.scheduleOnce(delay) {
          _finished = true
          applyTransition(transition).run()
        }
      }
    }
  }

  def event(name: Symbol, phase: EventPhase = Bubbling)(
      effect: => EventResult[F, S]): SimpleEvent[F, S, M] =
    SimpleEvent[F, S, M](name, phase, () => effect)

  def eventWithAccess(name: Symbol, phase: EventPhase = Bubbling)(
      effect: Access[F, S, M] => EventResult[F, S]): EventWithAccess[F, S, M] =
    EventWithAccess(name, phase, effect)

  def immediateTransition(transition: StateManager.Transition[S]): EventResult[F, S] =
    EventResult[F, S](Some(transition), None, sp = false)

  def deferredTransition(transition: F[StateManager.Transition[S]]): EventResult[F, S] =
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
  def apply[F[+_]: Async, S, M](implicit scheduler: Scheduler[F]) =
    new Effects[F, S, M]()

  abstract class Access[F[+_]: Async, S, M] {

    /**
      * Extract property of element from client-side DOM.
      * @param propName Name of property. 'value for example
      * @see [[Effects.elementId]]
      * @example
      * {{{
      * eventWithAccess('click) { access =>
      *   deferredTransition {
      *     for {
      *       request <- access.property[String]('value, searchField)
      *       result <- searchModel.search(request)
      *     } yield {
      *       transition {
      *         case state: State.Awesome =>
      *           state.copy(list = searchResult)
      *       }
      *     }
      *   }
      * }
      * }}}
      */
    def property[T](id: ElementId, propName: Symbol): F[T]

    /**
      * Publish message to environment
      */
    def publish(message: M): F[Unit]

    /** Downloads form from client
      * {{{
      * eventWithAccess('submit) { access => 
      *   access
      *     .downloadFormData(myForm)
      *     .onProgress { (loaded, total) =>
      *       // transition …
      *     }
      *     .start
      *     .map { formData =>
      *       val picture = data.file("picture") // Array[Byte]
      *       val title = data.text("title") // String
      *       // transition ...
      *     }
      * }
      * }}}
      * @param id form elementId
      * @return
      */
    def downloadFormData(id: ElementId): FormDataDownloader[F, S]
  }

  abstract class FormDataDownloader[F[+_]: Async, S] {
    def onProgress(f: (Int, Int) => Transition[S]): this.type
    def start(): F[FormData]
  }

  sealed abstract class Delay[F[+_]: Async, S] extends VDom.Misc {
    def start(applyTransition: Transition[S] => F[Unit]): Unit
    def finished: Boolean
    def cancel(): Unit
  }

  sealed abstract class Event[F[+_]: Async, S, M] extends VDom.Misc {
    def `type`: Symbol
    def phase: EventPhase
  }

  case class EventWithAccess[F[+_]: Async, S, M](
      `type`: Symbol,
      phase: EventPhase,
      effect: Access[F, S, M] => EventResult[F, S])
      extends Event[F, S, M]

  case class SimpleEvent[F[+_]: Async, S, M](`type`: Symbol,
                                      phase: EventPhase,
                                      effect: () => EventResult[F, S])
    extends Event[F, S, M]

  class ElementId extends VDom.Misc

}
