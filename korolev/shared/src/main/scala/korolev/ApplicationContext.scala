package korolev

import korolev.StateManager.Transition
import korolev.util.Scheduler
import korolev.Async.AsyncOps
import levsha.{Document, TemplateDsl}
import levsha.events.EventPhase

import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds

class ApplicationContext[F[+_]: Async, S, M](implicit scheduler: Scheduler[F]) {

  import ApplicationContext._
  import EventPhase._

  type Effect = ApplicationContext.Effect[F, S, M]
  type Event = ApplicationContext.Event[F, S, M]
  type EventFactory[T] = T => Event
  type Transition = StateManager.Transition[S]
  type Render = PartialFunction[S, Document.Node[Effect]]
  type ElementId = ApplicationContext.ElementId[F, S, M]

  final class ExtendedTemplateDsl extends TemplateDsl[ApplicationContext.Effect[F, S, M]] {

    type Document = levsha.Document[Effect]
    type Node     = levsha.Document.Node[Effect]
    type Attr     = levsha.Document.Attr[Effect]

    @deprecated("Use Node instead of VDom", "0.4.0")
    type VDom = Node

    implicit final class KorolevSymbolOps(s: Symbol) {
      def :=(value: String): Document.Attr[Effect] = Document.Attr { rc =>
        rc.setAttr('^' + s.name.replaceAll("([A-Z]+)", "-$1").toLowerCase, value)
      }
    }
  }

  val symbolDsl = new ExtendedTemplateDsl()

  def elementId = new ApplicationContext.ElementId[F, S, M]()

  /**
    * Schedules the [[transition]] with [[delay]]. For example it can be useful
    * when you want to hide something after timeout.
    */
  def delay(delay: FiniteDuration)(transition: Transition): Delay[F, S, M] = new Delay[F, S, M] {

    @volatile var _jobHandler = Option.empty[Scheduler.JobHandler[F, _]]
    @volatile var _finished = false

    def finished: Boolean = _finished

    def cancel(): Unit = _jobHandler.foreach(_.cancel())

    def start(applyTransition: Transition => F[Unit]): Unit = {
      _jobHandler = Some {
        scheduler.scheduleOnce(delay) {
          _finished = true
          applyTransition(transition).runIgnoreResult()
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

object ApplicationContext {

  sealed abstract class Effect[F[+_]: Async, S, M]

  /**
    * @tparam F Monad
    * @tparam S State
    * @tparam M Message
    */
  def apply[F[+_]: Async, S, M](implicit scheduler: Scheduler[F]) =
    new ApplicationContext[F, S, M]()

  abstract class Access[F[+_]: Async, S, M] {

    /**
      * Extracts property of element from client-side DOM.
      *
      * @param propName Name of property. 'value for example
      * @see [[ApplicationContext.elementId]]
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
    def property[T](id: ElementId[F, S, M], propName: Symbol): F[T]

    def property[T](id: ElementId[F, S, M]): PropertyHandler[F, T]

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
    def downloadFormData(id: ElementId[F, S, M]): FormDataDownloader[F, S]
  }

  abstract class PropertyHandler[F[+_]: Async, T] {
    def get(propName: Symbol): F[T]
    def set(propName: Symbol, value: T): F[Unit]
  }

  abstract class FormDataDownloader[F[+_]: Async, S] {
    def onProgress(f: (Int, Int) => Transition[S]): this.type
    def start(): F[FormData]
  }

  sealed abstract class Delay[F[+_]: Async, S, M] extends Effect[F, S, M] {
    def start(applyTransition: Transition[S] => F[Unit]): Unit
    def finished: Boolean
    def cancel(): Unit
  }

  sealed abstract class Event[F[+_]: Async, S, M] extends Effect[F, S, M] {
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

  class ElementId[F[+_]: Async, S, M] extends Effect[F, S, M]

}
