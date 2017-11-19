package korolev

import korolev.internal.{ClientSideApi, ComponentInstance, EventRegistry}
import korolev.state.{StateDeserializer, StateManager, StateSerializer}
import levsha._
import levsha.events.EventPhase

import scala.concurrent.duration.FiniteDuration

/**
  * Provides DSLs and effects for application or component
  * @since 0.6.0
  */
final class Context[F[+_]: Async, S: StateSerializer: StateDeserializer, M] {

  import Context._
  import EventPhase._

  type Effect = Context.Effect[F, S, M]
  type Event = Context.Event[F, S, M]
  type EventFactory[T] = T => Event
  type Transition = korolev.Transition[S]
  type Render = PartialFunction[S, Document.Node[Effect]]
  type ElementId = Context.ElementId[F, S, M]
  type Access = Context.Access[F, S, M]
  type EventResult = korolev.EventResult[F, S]

  val symbolDsl = new KorolevTemplateDsl[F, S, M]()

  @deprecated("This is compatibility layer for old fashioned API. Use Context instead.", "0.6.0")
  lazy val legacy = new ApplicationContext[F, S, M]

  def elementId(): ElementId = new Context.ElementId[F, S, M]()

  /**
    * Schedules the transition with delay. For example it can be useful
    * when you want to hide something after timeout.
    */
  def delay(duration: FiniteDuration)(effect: Access => F[Unit]): Delay[F, S, M] =
    Delay(duration, effect)

  def event(name: Symbol, phase: EventPhase = Bubbling)(
      effect: Access => EventResult): Event =
    Event(name, phase, effect)

  val emptyTransition: PartialFunction[S, S] = { case x => x }

  implicit def effectToEventResult(effect: F[Unit]): EventResult =
    EventResult(effect, stopPropagation = false)

  implicit final class EffectOps(effect: F[Unit]) {
    def stopPropagation: EventResult =
      EventResult(effect, stopPropagation = true)
  }

  implicit final class ComponentDsl[CS: StateSerializer: StateDeserializer, P, E](component: Component[F, CS, P, E]) {
    def apply(parameters: P)(f: (Access, E) => F[Unit]): ComponentEntry[F, S, M, CS, P, E] =
      ComponentEntry(component, parameters, f)

    def silent(parameters: P): ComponentEntry[F, S, M, CS, P, E] =
      ComponentEntry(component, parameters, (_, _) => Async[F].unit)
  }
}

object Context {

  /**
    * Creates new global context
    *
    * @tparam F Control monad
    * @tparam S Type of application state
    * @tparam M Type of events
    */
  def apply[F[+_]: Async, S: StateSerializer: StateDeserializer, M] = new Context[F, S, M]()

  /**
    * Provides access to make side effects
    */
  abstract class Access[F[+_]: Async, S, M] {

    /**
      * Extracts property of element from client-side DOM.
      *
      * @see [[Context.elementId]]
      * @since 0.6.0
      * @example
      * {{{
      * event('click) { access =>
      *   for {
      *     request <- access.property(searchField).get('value)
      *     result  <- searchModel.search(request)
      *     _       <- access.transition {
      *       case state: State.Awesome =>
      *         state.copy(list = searchResult)
      *     }
      *   } yield ()
      * }
      * }}}
      */
    def property(id: ElementId[F, S, M]): PropertyHandler[F]

    /**
      * Shortcut for `property(id).get(proName)`
      * @since 0.6.0
      */
    def property(id: ElementId[F, S, M], propName: Symbol): F[String]

    /**
      * Shortcut for `property(id).get('value)`
      * @since 0.6.0
      */
    def valueOf(id: ElementId[F, S, M]): F[String] = property(id, 'value)

    /**
      * Makes focus on the element
      */
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

    /**
      * Gives current state
      */
    def state: F[S]

    /**
      * Applies transition to current state
      */
    def transition(f: Transition[S]): F[Unit]

    /**
      * Gives current session id
      */
    def sessionId: F[QualifiedSessionId]

    /**
      * Gives json with string, number and boolean fields of
      * object of the event happened in current render phase.
      * Note that is expensive operation which requires
      * network round trip.
      */
    def eventData: F[String]
  }

  sealed abstract class Effect[F[+_]: Async, S, M]

  abstract class PropertyHandler[F[+_]: Async] {
    def get(propName: Symbol): F[String]
    def set(propName: Symbol, value: Any): F[Unit]
  }

  abstract class FormDataDownloader[F[+_]: Async, S] {
    def onProgress(f: (Int, Int) => Transition[S]): this.type
    def start(): F[FormData]
  }

  final case class ComponentEntry
    [
      F[+_]: Async,
      AS: StateSerializer: StateDeserializer, M,
      CS: StateSerializer: StateDeserializer, P, E
    ](
      component: Component[F, CS, P, E],
      parameters: P,
      eventHandler: (Access[F, AS, M], E) => F[Unit]
    )
    extends Effect[F, AS, M] {

    def createInstance(node: Id,
                       sessionId: QualifiedSessionId,
                       frontend: ClientSideApi[F],
                       eventRegistry: EventRegistry[F],
                       stateManager: StateManager[F],
                       getRenderNum: () => Int): ComponentInstance[F, AS, M, CS, P, E] = {
      new ComponentInstance(node, sessionId, frontend, eventRegistry, stateManager, getRenderNum, component)
    }
  }

  final case class Event[F[+_]: Async, S, M](
      `type`: Symbol,
      phase: EventPhase,
      effect: Access[F, S, M] => EventResult[F, S]) extends Effect[F, S, M]

  final case class Delay[F[+_]: Async, S, M](
      duration: FiniteDuration,
      effect: Access[F, S, M] => F[Unit]) extends Effect[F, S, M]

  final class ElementId[F[+_]: Async, S, M] extends Effect[F, S, M]
}
