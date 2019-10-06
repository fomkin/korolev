/*
 * Copyright 2017-2018 Aleksey Fomkin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
final class Context[F[_]: Async, S: StateSerializer: StateDeserializer, M] extends Context.Scope[F, S, S, M] {
  type AccessType = S
  protected val accessScope: Context.Access[F, S, M] => Context.Access[F, S, M] = identity _
}

object Context {

  /**
    * Creates new global context
    *
    * @tparam F Control monad
    * @tparam S Type of application state
    * @tparam M Type of events
    */
  def apply[F[_]: Async, S: StateSerializer: StateDeserializer, M] =
    new Context[F, S, M]()

  sealed abstract class Scope[F[_]: Async, S: StateSerializer: StateDeserializer, AccessType, M] {

    import EventPhase._

    type Effect = Context.Effect[F, S, M]
    type Event = Context.Event[F, S, M]
    type EventFactory[T] = T => Event
    type Transition = korolev.Transition[S]
    type Render = PartialFunction[S, Document.Node[Effect]]
    type ElementId = Context.ElementId[F]
    type Access = Context.Access[F, AccessType, M]
    type UnscopedAccess = Context.Access[F, S, M]
    type EventResult = F[Unit]

    val symbolDsl = new KorolevTemplateDsl[F, S, M]()

    protected val accessScope: Context.Access[F, S, M] => Access

    def scope[S2](read: PartialFunction[S, S2], write: PartialFunction[(S, S2), S]): Scope[F, S, S2, M] = new Scope[F, S, S2, M] {

      protected val accessScope: Context.Access[F, S, M] => Access = access => new Context.Access[F, S2, M] {

        def eventData: F[String] = access.eventData

        def property(id: Context.ElementId[F]): PropertyHandler[F] =
          access.property(id)

        def focus(id: Context.ElementId[F]): F[Unit] =
          access.focus(id)

        def publish(message: M): F[Unit] =
          access.publish(message)

        def downloadFormData(id: Context.ElementId[F]): FormDataDownloader[F, S2] =
          access.downloadFormData(id).scope(read, write)

        def downloadFiles(id: Context.ElementId[F]): F[List[File[Array[Byte]]]] =
          access.downloadFiles(id)

        def downloadFilesAsStream(id: Context.ElementId[F]): F[List[File[LazyBytes[F]]]] =
          access.downloadFilesAsStream(id)

        def state: F[S2] = Async[F].map(access.state)(read)

        def transition(f: korolev.Transition[S2]): F[Unit] =
          access.transition(s => write((s, f(read(s)))))

        def sessionId: F[QualifiedSessionId] = access.sessionId

        def evalJs(code: String): F[String] = access.evalJs(code)
      }
    }

    def elementId(name: Option[String] = None): ElementId = new Context.ElementId[F](name)

    /**
      * Schedules the transition with delay. For example it can be useful
      * when you want to hide something after timeout.
      */
    def delay(duration: FiniteDuration)(effect: Access => F[Unit]): Delay[F, S, M] = {
      Delay(duration, accessScope.andThen(effect))
    }

    @deprecated("""Use "eventName" instead of 'eventName""", "0.13.0")
    def eventUnscoped(name: Symbol)(
      effect: UnscopedAccess => F[Unit]): Event =
      Event(name.name, Bubbling, effect)

    @deprecated("""Use "eventName" instead of 'eventName""", "0.13.0")
    def event(name: Symbol)(
      effect: Access => F[Unit]): Event =
      Event(name.name, Bubbling, accessScope.andThen(effect))

    @deprecated("""Use "eventName" instead of 'eventName""", "0.13.0")
    def eventUnscoped(name: Symbol, phase: EventPhase)(
      effect: UnscopedAccess => F[Unit]): Event =
      Event(name.name, phase, effect)

    @deprecated("""Use "eventName" instead of 'eventName""", "0.13.0")
    def event(name: Symbol, phase: EventPhase)(
      effect: Access => F[Unit]): Event =
      Event(name.name, phase, accessScope.andThen(effect))

    def event(name: String)(
      effect: Access => F[Unit]): Event =
      Event(name, Bubbling, accessScope.andThen(effect))

    def eventUnscoped(name: String)(
      effect: UnscopedAccess => F[Unit]): Event =
      Event(name, Bubbling, effect)

    def event(name: String, phase: EventPhase)(
      effect: Access => F[Unit]): Event =
      Event(name, phase, accessScope.andThen(effect))

    def eventUnscoped(name: String, phase: EventPhase)(
      effect: UnscopedAccess => F[Unit]): Event =
      Event(name, phase, effect)

    val emptyTransition: PartialFunction[S, S] = { case x => x }

    implicit final class ComponentDsl[CS: StateSerializer: StateDeserializer, P, E](component: Component[F, CS, P, E]) {
      def apply(parameters: P)(f: (Access, E) => F[Unit]): ComponentEntry[F, S, M, CS, P, E] =
        ComponentEntry(component, parameters, (a: Context.Access[F, S, M], e: E) => f(accessScope(a), e))

      def silent(parameters: P): ComponentEntry[F, S, M, CS, P, E] =
        ComponentEntry(component, parameters, (_, _) => Async[F].unit)
    }
  }

  trait BaseAccess[F[_], S, M] {

    /**
      * Extracts property of element from client-side DOM.
      *
      * @see [[Scope.elementId]]
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
    def property(id: ElementId[F]): PropertyHandler[F]

    /**
      * Shortcut for `property(id).get(propName)`.
      * @since 0.6.0
      */
    @deprecated("""Use "propertyName" instead of 'propertyName""", "0.13.0")
    final def property(id: ElementId[F], propName: Symbol): F[String] = property(id).get(propName)

    /**
      * Shortcut for `property(id).get(propName)`.
      * @since 0.13.0
      */
    final def property(id: ElementId[F], propName: String): F[String] = property(id).get(propName)

    /**
      * Shortcut for `property(id).get('value)`.
      * @since 0.6.0
      */
    final def valueOf(id: ElementId[F]): F[String] = property(id, "value")

    /**
      * Makes focus on the element
      */
    def focus(id: ElementId[F]): F[Unit]

    /**
      * Publish message to environment.
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
    def downloadFormData(id: ElementId[F]): FormDataDownloader[F, S]

    /**
      * Download selected file list from input correspondent to given element id.
      */
    def downloadFiles(id: ElementId[F]): F[List[File[Array[Byte]]]]

    /**
      * Same as [[downloadFiles]] but for stream mode. The method is useful
      * when user want to upload very large files list which is problematic
      * to keep in memory (especially when count of users is more than one).
      */
    def downloadFilesAsStream(id: ElementId[F]): F[List[File[LazyBytes[F]]]]

    /**
      * Gives current state.
      */
    def state: F[S]

    /**
      * Applies transition to current state.
      */
    def transition(f: Transition[S]): F[Unit]

    /**
      * Applies transition to current state.
      */
    def maybeTransition(f: PartialFunction[S, S]): F[Unit] = transition(f)

    /**
      * Gives current session id.
      */
    def sessionId: F[QualifiedSessionId]

    /**
      * Execute arbitrary JavaScript code on client and get stringified JSON back.
      * {{{
      * access.evalJs("new Date().getTimezoneOffset()").map(offset => ...)
      * }}}
      */
    def evalJs(code: String): F[String]

  }

  trait EventAccess[F[_], S, M] {

    /**
      * Gives json with string, number and boolean fields of
      * object of the event happened in current render phase.
      * Note that is expensive operation which requires
      * network round trip.
      */
    def eventData: F[String]

  }

  /**
    * Provides access to make side effects
    */
  abstract class Access[F[_]: Async, S, M] extends BaseAccess[F, S, M] with EventAccess[F, S, M]

  sealed abstract class Effect[F[_]: Async, +S, +M]

  abstract class PropertyHandler[F[_]: Async] {
    @deprecated("""Use "propertyName" instead of 'propertyName""", "0.13.0")
    def get(propName: Symbol): F[String]
    @deprecated("""Use "propertyName" instead of 'propertyName""", "0.13.0")
    def set(propName: Symbol, value: Any): F[Unit]
    def get(propName: String): F[String]
    def set(propName: String, value: Any): F[Unit]
  }

  final case class File[A](name: String, data: A)

  abstract class FormDataDownloader[F[_]: Async, S] { self =>
    def onProgress(f: (Int, Int) => Transition[S]): this.type
    def start(): F[FormData]
    def scope[S2](read: PartialFunction[S, S2], write: PartialFunction[(S, S2), S]): FormDataDownloader[F, S2] = {
      new FormDataDownloader[F, S2] {
        def onProgress(f: (Int, Int) => Transition[S2]): this.type = {
          self.onProgress((a, b) => (s: S) => write((s, f(a, b)(read(s)))))
          this
        }
        def start(): F[FormData] =
          self.start()
      }
    }
  }

  final case class ComponentEntry
    [
      F[_]: Async,
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
                       stateManager: StateManager[F, CS],
                       getRenderNum: () => Int,
                       reporter: Reporter): ComponentInstance[F, AS, M, CS, P, E] = {
      new ComponentInstance(node, sessionId, frontend, eventRegistry, stateManager, getRenderNum, component, reporter)
    }
  }

  final case class Event[F[_]: Async, S, M](
      `type`: String,
      phase: EventPhase,
      effect: Access[F, S, M] => F[Unit]) extends Effect[F, S, M]

  final case class Delay[F[_]: Async, S, M](
      duration: FiniteDuration,
      effect: Access[F, S, M] => F[Unit]) extends Effect[F, S, M]

  final class ElementId[F[_]: Async](val name: Option[String]) extends Effect[F, Nothing, Nothing] {
    override def equals(obj: Any): Boolean = obj match {
      case other: ElementId[F] => if (name.isDefined) name == other.name else super.equals(other)
      case _ => false
    }

    override def hashCode(): Int = if (name.isDefined) name.hashCode() else super.hashCode()
  }
}
