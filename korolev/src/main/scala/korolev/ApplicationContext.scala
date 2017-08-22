package korolev

import korolev.execution.Scheduler
import korolev.internal.{ClientSideApi, ComponentInstance, EventRegistry}
import levsha._
import levsha.events.EventPhase

import scala.concurrent.duration.FiniteDuration

class ApplicationContext[F[+_]: Async: Scheduler, S, M] {

  import ApplicationContext._
  import EventPhase._

  type Effect = ApplicationContext.Effect[F, S, M]
  type Event = ApplicationContext.Event[F, S, M]
  type EventFactory[T] = T => Event
  type Transition = korolev.Transition[S]
  type Render = PartialFunction[S, Document.Node[Effect]]
  type ElementId = ApplicationContext.ElementId[F, S, M]

  val symbolDsl = new KorolevTemplateDsl[F, S, M]()

  def elementId = new ApplicationContext.ElementId[F, S, M]()

  /**
    * Schedules the [[transition]] with [[delay]]. For example it can be useful
    * when you want to hide something after timeout.
    */
  def delay(delay: FiniteDuration)(transition: Transition): Delay[F, S, M] = new Delay[F, S, M] {

    @volatile private var handler = Option.empty[Scheduler.JobHandler[F, _]]
    @volatile private var finished = false

    def isFinished: Boolean = finished

    def cancel(): Unit = {
      handler.foreach(_.cancel())
    }

    def start(applyTransition: Transition => Unit): Unit = {
      handler = Some {
        Scheduler[F].scheduleOnce(delay) {
          finished = true
          applyTransition(transition)
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

  def immediateTransition(transition: Transition): EventResult[F, S] =
    EventResult[F, S](Some(transition), None, sp = false)

  def deferredTransition(transition: F[Transition]): EventResult[F, S] =
    EventResult[F, S](None, Some(transition), sp = false)

  /**
    * This is an immediateTransition return same state
    */
  def noTransition: EventResult[F, S] = immediateTransition {
    case anyState => anyState
  }

  val emptyTransition: PartialFunction[S, S] = { case x => x }

  def transition(t: Transition): Transition = t

  implicit final class ComponentDsl[CS, P, E](component: Component[F, CS, P, E]) {
    def apply(parameters: P)(f: (Access[F, S, M], E) => EventResult[F, S]): ComponentEntry[F, S, M, CS, P, E] =
      ComponentEntry(component, parameters, f)

    def silent(parameters: P): ComponentEntry[F, S, M, CS, P, E] =
      ComponentEntry(component, parameters, (_, _) => EventResult())
  }
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
      *       request <- access.property('value, searchField)
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
    def property(id: ElementId[F, S, M], propName: Symbol): F[String]

    def property(id: ElementId[F, S, M]): PropertyHandler[F]

    def focus(id: ElementId[F, S, M]): F[Unit]

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

  abstract class PropertyHandler[F[+_]: Async] {
    def get(propName: Symbol): F[String]
    def set(propName: Symbol, value: Any): F[Unit]
  }

  abstract class FormDataDownloader[F[+_]: Async, S] {
    def onProgress(f: (Int, Int) => Transition[S]): this.type
    def start(): F[FormData]
  }

  sealed abstract class Delay[F[+_]: Async, S, M] extends Effect[F, S, M] {
    def start(applyTransition: Transition[S] => Unit): Unit
    def isFinished: Boolean
    def cancel(): Unit
  }

  final case class ComponentEntry[F[+_]: Async, AS, M, CS, P, E](component: Component[F, CS, P, E],
                                                                 parameters: P,
                                                                 eventHandler: (Access[F, AS, M], E) => EventResult[F, AS]) extends Effect[F, AS, M] {

    def createInstance(node: Id, frontend: ClientSideApi[F], eventRegistry: EventRegistry[F]): ComponentInstance[F, AS, M, CS, P, E] = {
      new ComponentInstance(node, frontend, eventRegistry, component)
    }
  }

  sealed abstract class Event[F[+_]: Async, S, M] extends Effect[F, S, M] {
    def `type`: Symbol
    def phase: EventPhase
  }

  final case class EventWithAccess[F[+_]: Async, S, M](
      `type`: Symbol,
      phase: EventPhase,
      effect: Access[F, S, M] => EventResult[F, S])
      extends Event[F, S, M]

  final case class SimpleEvent[F[+_]: Async, S, M](`type`: Symbol,
                                      phase: EventPhase,
                                      effect: () => EventResult[F, S])
    extends Event[F, S, M]

  final class ElementId[F[+_]: Async, S, M] extends Effect[F, S, M]

}
