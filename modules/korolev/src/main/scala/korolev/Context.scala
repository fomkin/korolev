/*
 * Copyright 2017-2020 Aleksey Fomkin
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

import korolev.data.{Bytes, BytesLike}
import korolev.effect.{Effect, Reporter, Scheduler, Stream}
import korolev.internal.{ComponentInstance, EventRegistry, Frontend}
import korolev.state.{StateDeserializer, StateManager, StateSerializer}
import korolev.util.JsCode
import korolev.web.FormData
import levsha._
import levsha.events.EventPhase

import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

/**
  * Provides DSLs and effects for application or component
  * @since 0.6.0
  */
final class Context[F[_]: Effect, S: StateSerializer: StateDeserializer] extends Context.Scope[F, S, S] {
  type AccessType = S
  protected val accessScope: Context.Access[F, S] => Context.Access[F, S] = identity
}

object Context {

  /**
    * Creates new global context
    *
    * @tparam F Control monad
    * @tparam S Type of application state
    * @tparam M Type of events
    */
  def apply[F[_]: Effect, S: StateSerializer: StateDeserializer] =
    new Context[F, S]()

  sealed abstract class Scope[F[_]: Effect, S: StateSerializer: StateDeserializer, AccessType] {

    type Binding = Context.Binding[F, S]
    type Event = Context.Event[F, S]
    type EventFactory[T] = T => Event
    type Transition = korolev.Transition[S]
    type Render = PartialFunction[S, Document.Node[Binding]]
    type ElementId = Context.ElementId
    type Access = Context.Access[F, AccessType]
    type UnscopedAccess = Context.Access[F, S]
    type EventResult = F[Unit]
    type Document = levsha.Document[Binding]
    type Node = levsha.Document.Node[Binding]
    type Attr = levsha.Document.Attr[Binding]

    val symbolDsl = new KorolevTemplateDsl[F, S]()

    protected val accessScope: Context.Access[F, S] => Access

    def scope[S2](read: PartialFunction[S, S2], write: PartialFunction[(S, S2), S]): Scope[F, S, S2] = new Scope[F, S, S2] {

      protected val accessScope: Context.Access[F, S] => Access = access => new Context.Access[F, S2] {

        def eventData: F[String] = access.eventData

        def property(id: Context.ElementId): PropertyHandler[F] =
          access.property(id)

        def focus(id: Context.ElementId): F[Unit] =
          access.focus(id)

        def publish[M: ClassTag](message: M): F[Unit] =
          access.publish(message)

        def downloadFormData(id: Context.ElementId): F[FormData] =
          access.downloadFormData(id)

        def downloadFiles(id: Context.ElementId): F[List[(FileHandler, Bytes)]] =
          access.downloadFiles(id)

        def downloadFilesAsStream(id: Context.ElementId): F[List[(FileHandler, Stream[F, Bytes])]] =
          access.downloadFilesAsStream(id)

        def downloadFileAsStream(handler: FileHandler): F[Stream[F, Bytes]] =
          access.downloadFileAsStream(handler)

        def listFiles(id: Context.ElementId): F[List[FileHandler]] =
          access.listFiles(id)

        def resetForm(id: Context.ElementId): F[Unit] =
          access.resetForm(id)

        def state: F[S2] = Effect[F].map(access.state)(read)

        def transition(f: korolev.Transition[S2]): F[Unit] =
          access.transition(s => write((s, f(read(s)))))

        def syncTransition(f: korolev.Transition[S2]): F[Unit] =
          access.syncTransition(s => write((s, f(read(s)))))

        def sessionId: F[Qsid] = access.sessionId

        def evalJs(code: JsCode): F[String] = access.evalJs(code)

        def registerCallback(name: String)(f: String => F[Unit]): F[Unit] =
          access.registerCallback(name)(f)
      }
    }

    implicit class JsCodeHelper(sc: StringContext) {
      def js(args: Any*): JsCode =
        JsCode(sc.parts.toList, args.toList)
    }

    def elementId(name: Option[String] = None): ElementId = new Context.ElementId(name)

    /**
      * Schedules the transition with delay. For example it can be useful
      * when you want to hide something after timeout.
      */
    def delay(duration: FiniteDuration)(effect: Access => F[Unit]): Delay[F, S] = {
      Delay(duration, accessScope.andThen(effect))
    }

    def event(name: String, stopPropagation: Boolean = false, phase: EventPhase = EventPhase.Bubbling)(
      effect: Access => F[Unit]): Event =
      Event(name, phase, stopPropagation, accessScope.andThen(effect))

    def eventUnscoped(name: String, stopPropagation: Boolean = false, phase: EventPhase = EventPhase.Bubbling)(
      effect: UnscopedAccess => F[Unit]): Event =
      Event(name, phase, stopPropagation, effect)

    val emptyTransition: PartialFunction[S, S] = { case x => x }

    implicit final class ComponentDsl[CS: StateSerializer: StateDeserializer, P, E](component: Component[F, CS, P, E]) {
      def apply(parameters: P)(f: (Access, E) => F[Unit]): ComponentEntry[F, S, CS, P, E] =
        ComponentEntry(component, parameters, (a: Context.Access[F, S], e: E) => f(accessScope(a), e))

      def silent(parameters: P): ComponentEntry[F, S, CS, P, E] =
        ComponentEntry(component, parameters, (_, _) => Effect[F].unit)
    }
  }

  trait BaseAccess[F[_], S] {

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
    def property(id: ElementId): PropertyHandler[F]

    /**
      * Shortcut for `property(id).get(propName)`.
      * @since 0.6.0
      */
    @deprecated("""Use "propertyName" instead of 'propertyName""", "0.13.0")
    final def property(id: ElementId, propName: Symbol): F[String] = property(id).get(propName)

    /**
      * Shortcut for `property(id).get(propName)`.
      * @since 0.13.0
      */
    final def property(id: ElementId, propName: String): F[String] = property(id).get(propName)

    /**
      * Shortcut for `property(id).get('value)`.
      * @since 0.6.0
      */
    final def valueOf(id: ElementId): F[String] = property(id, "value")

    /**
      * Makes focus on the element
      */
    def focus(id: ElementId): F[Unit]

    /**
      * Publish message to environment.
      */
    def publish[M: ClassTag](message: M): F[Unit]

    /**
      * Downloads form from the client. Useful when when you
      * want to read big amount of fields. Do not use this
      * method for downloading files, however it is possible.
      *
      * {{{
      * event(submit) { access =>
      *   access
      *     .downloadFormData(myForm)
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
    def downloadFormData(id: ElementId): F[FormData]

    /**
      * Download the selected file list from input appropriate
      * to given element id. Use this method carefully because
      * all files are saving to RAM.
      */
    def downloadFiles(id: ElementId): F[List[(FileHandler, Bytes)]]

    /**
      * Same as [[downloadFiles]] but for stream mode. The method is useful
      * when user want to upload very large files list which is problematic
      * to keep in memory (especially when count of users is more than one).
      */
    def downloadFilesAsStream(id: ElementId): F[List[(FileHandler, Stream[F, Bytes])]]

    /**
      * Get selected file as a stream from input
      */
    def downloadFileAsStream(handler: FileHandler): F[Stream[F, Bytes]]

    /**
      * Get only file list for input
      */
    def listFiles(id: ElementId): F[List[FileHandler]]

    /**
      * Purge inputs in given form.
      * @param id form element id binding
      */
    def resetForm(id: ElementId): F[Unit]

    /**
      * Gives current state.
      */
    def state: F[S]

    /**
      * Applies transition to current state.
      */
    def transition(f: Transition[S]): F[Unit]

    /**
      * Applies transition to current state
      * and awaits render.
      */
    def syncTransition(f: Transition[S]): F[Unit]

    /**
      * Applies transition to current state.
      */
    final def maybeTransition(f: PartialFunction[S, S]): F[Unit] =
      transition(s => f.applyOrElse(s, identity[S]))

    /**
      * Gives current session id.
      */
    def sessionId: F[Qsid]

    /**
      * Execute arbitrary JavaScript code on client and get stringified JSON back.
      * {{{
      * for {
      *   _ <- access.evalJs("new Date().getTimezoneOffset()").map(offset => ...)
      *   _ <- access.evalJs(js"$myForm.submit()").map(offset => ...)
      * } yield ()
      * }}}
      */
    def evalJs(code: JsCode): F[String]

    def evalJs(code: String): F[String] =
      evalJs(JsCode(code))

    /**
     * Register callback that could be invoked from the client side.
     *
     * {{{
     *   // Scala
     *   access.registerCallback("myCallback") { myArg =>
     *     Future(println(myArg))
     *   }
     *
     *   // JavaScript
     *   Korolev.invokeCallback('myCallback', 'myArgValue');
     * }}}
     */
    def registerCallback(name: String)(f: String => F[Unit]): F[Unit]
  }

  trait EventAccess[F[_], S] {

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
  trait Access[F[_], S] extends BaseAccess[F, S] with EventAccess[F, S]

  sealed trait Binding[+F[_], +S]

  abstract class PropertyHandler[F[_]: Effect] {
    @deprecated("""Use "propertyName" instead of 'propertyName""", "0.13.0")
    def get(propName: Symbol): F[String] = get(propName.name)
    @deprecated("""Use "propertyName" instead of 'propertyName""", "0.13.0")
    def set(propName: Symbol, value: Any): F[Unit] = set(propName.name, value)
    def get(propName: String): F[String]
    def set(propName: String, value: Any): F[Unit]
  }

  final case class FileHandler(fileName: String, size: Long)(private[korolev] val elementId: ElementId)

  final case class ComponentEntry
    [
      F[_]: Effect,
      AS: StateSerializer: StateDeserializer,
      CS: StateSerializer: StateDeserializer, P, E
    ](
      component: Component[F, CS, P, E],
      parameters: P,
      eventHandler: (Access[F, AS], E) => F[Unit]
    )
    extends Binding[F, AS] {

    def createInstance(node: Id,
                       sessionId: Qsid,
                       frontend: Frontend[F],
                       eventRegistry: EventRegistry[F],
                       stateManager: StateManager[F],
                       getRenderNum: () => Int,
                       notifyStateChange: (Id, Any) => F[Unit],
                       scheduler: Scheduler[F],
                       reporter: Reporter): ComponentInstance[F, AS, CS, P, E] = {
      new ComponentInstance(
        node, sessionId, frontend, eventRegistry, stateManager, getRenderNum, component, notifyStateChange,
        createMiscProxy = (rc, k) => new StatefulRenderContext[Binding[F, CS]] {
          def currentContainerId: Id = rc.currentContainerId
          def currentId: Id = rc.currentId
          def subsequentId: Id = rc.subsequentId
          def openNode(xmlns: XmlNs, name: String): Unit = rc.openNode(xmlns, name)
          def closeNode(name: String): Unit = rc.closeNode(name)
          def setAttr(xmlNs: XmlNs, name: String, value: String): Unit = rc.setAttr(xmlNs, name, value)
          def setStyle(name: String, value: String): Unit = rc.setStyle(name, value) 
          def addTextNode(text: String): Unit = rc.addTextNode(text)
          def addMisc(misc: Binding[F, CS]): Unit = k(this, misc)
        },
        scheduler, reporter
      )
    }
  }

  final case class Event[F[_], S](
      `type`: String,
      phase: EventPhase,
      stopPropagation: Boolean,
      effect: Access[F, S] => F[Unit]) extends Binding[F, S]

  final case class Delay[F[_]: Effect, S](
      duration: FiniteDuration,
      effect: Access[F, S] => F[Unit]) extends Binding[F, S]

  final class ElementId (val name: Option[String]) extends Binding[Nothing, Nothing, Nothing] {
    override def equals(obj: Any): Boolean = obj match {
      case other: ElementId => if (name.isDefined) name == other.name else super.equals(other)
      case _ => false
    }
    override def toString: String = name match {
      case Some(x) => s"ElementId($x)"
      case None => super.toString
    }
    override def hashCode(): Int = if (name.isDefined) name.hashCode() else super.hashCode()
  }
}
