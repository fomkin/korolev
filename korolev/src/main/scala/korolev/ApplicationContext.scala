package korolev

import korolev.execution.Scheduler
import korolev.internal.{ClientSideApi, ComponentInstance, EventRegistry}
import levsha._
import levsha.events.EventPhase
import Async._

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
  def delay(delay: FiniteDuration)(effect: Access[F, S, M] => F[Unit]): Delay[F, S, M] = new Delay[F, S, M] {

    @volatile private var handler = Option.empty[Scheduler.JobHandler[F, _]]
    @volatile private var finished = false

    def isFinished: Boolean = finished

    def cancel(): Unit = {
      handler.foreach(_.cancel())
    }

    def start(access: Access[F, S, M]): Unit = {
      handler = Some {
        Scheduler[F].scheduleOnce(delay) {
          finished = true
          effect(access).runIgnoreResult
        }
      }
    }
  }

//  def event(name: Symbol, phase: EventPhase = Bubbling)(
//      effect: => EventResult[F, S]): SimpleEvent[F, S, M] =
//    SimpleEvent[F, S, M](name, phase, () => effect)

  def event(name: Symbol, phase: EventPhase = Bubbling)(
      effect: Access[F, S, M] => EventResult[F, S]): Event =
    Event(name, phase, effect)

//  def immediateTransition(transition: Transition): EventResult[F, S] =
//    EventResult[F, S](Some(transition), None, sp = false)

//  def deferredTransition(transition: F[Transition]): EventResult[F, S] =
//    EventResult[F, S](None, Some(transition), sp = false)

//  /**
//    * This is an immediateTransition return same state
//    */
//  def noTransition: EventResult[F, S] = immediateTransition {
//    case anyState => anyState
//  }

  val emptyTransition: PartialFunction[S, S] = { case x => x }

  def transition(t: Transition): Transition = t

  implicit def effectToEventResult(effect: F[Unit]): EventResult[F, S] =
    EventResult(effect, stopPropagation = false)

  implicit final class EffectOps(effect: F[Unit]) {
    def stopPropagation: EventResult[F, S] =
      EventResult(effect, stopPropagation = true)
  }

  implicit final class ComponentDsl[CS, P, E](component: Component[F, CS, P, E]) {
    def apply(parameters: P)(f: (Access[F, S, M], E) => F[Unit]): ComponentEntry[F, S, M, CS, P, E] =
      ComponentEntry(component, parameters, f)

    def silent(parameters: P): ComponentEntry[F, S, M, CS, P, E] =
      ComponentEntry(component, parameters, (_, _) => Async[F].unit)
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
      * event('click) { access =>
      *   for {
      *     request <- access.property('value, searchField)
      *     result  <- searchModel.search(request)
      *     _       <- access.transition {
      *       case state: State.Awesome =>
      *         state.copy(list = searchResult)
      *     }
      *   } yield ()
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
      * event('submit) { access =>
      *   access
      *     .downloadFormData(myForm)
      *     .onProgress { (loaded, total) =>
      *       // transition …
      *     }
      *     .start
      *     .flatMap { formData =>
      *       val picture = data.file("picture") // Array[Byte]
      *       val title = data.text("title") // String
      *       access.transition {
      *         // ... transtion
      *       }
      *     }
      * }
      * }}}
      * @param id form elementId
      * @return
      */
    def downloadFormData(id: ElementId[F, S, M]): FormDataDownloader[F, S]

    def state: F[S]

    def transition(f: Transition[S]): F[Unit]
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
    def start(access: Access[F, S, M]): Unit
    def isFinished: Boolean
    def cancel(): Unit
  }

  final case class ComponentEntry[F[+_]: Async, AS, M, CS, P, E](component: Component[F, CS, P, E],
                                                                 parameters: P,
                                                                 eventHandler: (Access[F, AS, M], E) => F[Unit]) extends Effect[F, AS, M] {

    def createInstance(node: Id, frontend: ClientSideApi[F], eventRegistry: EventRegistry[F]): ComponentInstance[F, AS, M, CS, P, E] = {
      new ComponentInstance(node, frontend, eventRegistry, component)
    }
  }

  final case class Event[F[+_]: Async, S, M](
      `type`: Symbol,
      phase: EventPhase,
      effect: Access[F, S, M] => EventResult[F, S]) extends Effect[F, S, M]

  final class ElementId[F[+_]: Async, S, M] extends Effect[F, S, M]
}
