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
import korolev.effect.{Effect, Queue, Reporter, Scheduler, Stream}
import korolev.internal.{ComponentInstance, EventRegistry, Frontend}
import korolev.state.{StateDeserializer, StateManager, StateSerializer}
import korolev.util.{JsCode, Lens}
import korolev.web.{FormData, MimeTypes}
import levsha.*
import levsha.events.EventPhase

import scala.concurrent.duration.FiniteDuration

/**
  * Provides DSLs and effects for application or component
  * @since 0.6.0
  */
final class Context[F[_], S, M] extends Context.Scope[F, S, S, M] {
  type AccessType = S
  protected val accessScope: Context.Access[F, S, M] => Context.Access[F, S, M] = identity
}

object Context {

  /**
    * Creates new global context
    *
    * @tparam F Control monad
    * @tparam S Type of application state
    * @tparam M Type of events
    */
  def apply[F[_], S, M] =
    new Context[F, S, M]()

  sealed abstract class Scope[F[_], S, AccessType, M] {

    type Binding = Context.Binding[F, S, M]
    type Event = Context.Event[F, S, M]
    type EventFactory[T] = T => Event
    type Transition = korolev.Transition[S]
    type Render = PartialFunction[S, Document.Node[Binding]]
    type ElementId = Context.ElementId
    type Access = Context.Access[F, AccessType, M]
    type UnscopedAccess = Context.Access[F, S, M]
    type EventResult = F[Unit]
    type Document = levsha.Document[Binding]
    type Node = levsha.Document.Node[Binding]
    type Attr = levsha.Document.Attr[Binding]

    protected val accessScope: Context.Access[F, S, M] => Access

    def scope[S2](lens: Lens[S, S2]): Scope[F, S, S2, M] =
      scope(lens.read, lens.write)

    def scope[S2](read: PartialFunction[S, S2], write: PartialFunction[(S, S2), S]): Scope[F, S, S2, M] =
      new Scope[F, S, S2, M] {
        protected val accessScope: Context.Access[F, S, M] => Access =
          access => access.imap(read, write)
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
    @deprecated("Delays often is using for hacks", "0.18.0")
    def delay(duration: FiniteDuration)(effect: Access => F[Unit]): Delay[F, S, M] = {
      Delay(duration, accessScope.andThen(effect))
    }

    def event(name: String, stopPropagation: Boolean = false, phase: EventPhase = EventPhase.Bubbling)(
      effect: Access => F[Unit]): Event =
      Event(name, phase, stopPropagation, accessScope.andThen(effect))

    def eventUnscoped(name: String, stopPropagation: Boolean = false, phase: EventPhase = EventPhase.Bubbling)(
      effect: UnscopedAccess => F[Unit]): Event =
      Event(name, phase, stopPropagation, effect)

    val emptyTransition: PartialFunction[S, S] = { case x => x }

    implicit final class ComponentDsl[CS: StateSerializer: StateDeserializer, P, E](component: ComponentBase[F, CS, P, E])
                                                                                   (implicit _e: Effect[F],
                                                                                    _css: StateSerializer[S],
                                                                                    _csd: StateDeserializer[S]) {
      def apply(parameters: P)(f: (Access, E) => F[Unit]): ComponentEntry[F, S, M, CS, P, E] =
        ComponentEntry(component, parameters, (a: Context.Access[F, S, M], e: E) => f(accessScope(a), e))

      def silent(parameters: P): ComponentEntry[F, S, M, CS, P, E] =
        ComponentEntry(component, parameters, (_, _) => Effect[F].unit)
    }
  }

  trait BaseAccess[F[_], S, M] {

    def imap[S2](lens: Lens[S, S2]): Access[F, S2, M]

    def imap[S2](map: PartialFunction[S, S2], contramap: PartialFunction[(S, S2), S]): Access[F, S2, M] =
      imap(Lens(map, contramap))

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
    def publish(message: M): F[Unit]

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
      * Download file from the client side by file handler
      */
    def downloadFileAsStream(handler: FileHandler): F[Stream[F, Bytes]]

    /**
      * Get only file list for input
      */
    def listFiles(id: ElementId): F[List[FileHandler]]

    /**
     * Upload stream to the client side with selected name, size and mimeType
     */
    def uploadFile(name: String,
                   stream: Stream[F, Bytes],
                   size: Option[Long],
                   mimeType: String = MimeTypes.`application/octet-stream`): F[Unit]

    /**
      * Purge inputs in given form.
      * @param id form element id binding
      */
    def resetForm(id: ElementId): F[Unit]

    /**
      * Gives current state.
      */
    def state: F[S]

    def stateFocus[B](lens: Lens[S, B]): F[B]

    /**
      * Applies transition to current state.
      */
    def transition(f: Transition[S]): F[Unit]

    def transition[B](lens: Lens[S, B])(f: Transition[B]): F[Unit]

    /**
     * Applies asynchronous transition to current state. All transitions
     * will wait until this transition will executed.
     *
     * NOTE: Do not use this method id you work with effects
     * which take lot of time for execution. It's may lead to hanging
     * of your app.
     * @return
     */
    def transitionAsync(f: TransitionAsync[F, S]): F[Unit]

    def transitionAsync[B](lens: Lens[S, B])(f: TransitionAsync[F, B]): F[Unit]

    /**
     * Applies transition to current state and awaits render.
     */
    def transitionForce(f: Transition[S]): F[Unit]

    def transitionForce[B](lens: Lens[S, B])(f: Transition[B]): F[Unit]

    /**
     * @see [[transitionForce]]
     * @see [[transitionAsync]]
     */
    def transitionForceAsync(f: TransitionAsync[F, S]): F[Unit]

    def transitionForceAsync[B](lens: Lens[S, B])(f: TransitionAsync[F, B]): F[Unit]

    @deprecated("Use transitionForce instead", since = "1.5.0")
    def syncTransition(f: Transition[S]): F[Unit] = transitionForce(f)

    // TODO maybeTransitionAsync, maybeTransitionForceAsync
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
  trait Access[F[_], S, M] extends BaseAccess[F, S, M] with EventAccess[F, S, M]

  private[korolev] abstract class BaseAccessDefault[F[_]: Effect, S, M] extends Access[F, S, M] {

    import korolev.effect.syntax._

    def imap[S2](lens: Lens[S, S2]): Access[F, S2, M] = new MappedAccess[F, S, S2, M](this, lens)

    def stateFocus[B](lens: Lens[S, B]): F[B] = state.map(lens.read)

    def transitionAsync[B](lens: Lens[S, B])(f: TransitionAsync[F, B]): F[Unit] =
      transitionAsync(transitionAsyncWithLens(lens, f))

    def transitionForceAsync[B](lens: Lens[S, B])(f: TransitionAsync[F, B]): F[Unit] =
      transitionForceAsync(transitionAsyncWithLens(lens, f))

    def transition[B](lens: Lens[S, B])(f: Transition[B]): F[Unit] =
      transition(x => lens.modify(x)(f))

    def transitionForce[B](lens: Lens[S, B])(f: Transition[B]): F[Unit] =
      transitionForce(x => lens.modify(x)(f))

    private def transitionAsyncWithLens[B](lens: Lens[S, B], f: TransitionAsync[F, B]): TransitionAsync[F, S] = a =>
      lens.get(a).fold(Effect[F].pure(a)) { b =>
        f(b).map(b2 => lens.modify(a)(_ => b2))
      }
  }

  class MappedAccess[F[_]: Effect, S1, SN, E](self: Access[F, S1, E], lens: Lens[S1, SN]) extends Access[F, SN, E] { mapped =>
    private final val read = lens.read
    private final val write = lens.write
    def imap[S2](lens: Lens[SN, S2]): Access[F, S2, E] = new MappedAccess[F, SN, S2, E](this, lens)
    def eventData: F[String] = self.eventData
    def property(id: Context.ElementId): PropertyHandler[F] = self.property(id)
    def focus(id: Context.ElementId): F[Unit] = self.focus(id)
    def publish(message: E): F[Unit] = self.publish(message)
    def downloadFormData(id: Context.ElementId): F[FormData] = self.downloadFormData(id)
    def downloadFiles(id: Context.ElementId): F[List[(FileHandler, Bytes)]] = self.downloadFiles(id)
    def downloadFilesAsStream(id: Context.ElementId): F[List[(FileHandler, Stream[F, Bytes])]] = self.downloadFilesAsStream(id)
    def downloadFileAsStream(handler: FileHandler): F[Stream[F, Bytes]] = self.downloadFileAsStream(handler)
    def listFiles(id: Context.ElementId): F[List[FileHandler]] = self.listFiles(id)
    def uploadFile(name: String, stream: Stream[F, Bytes], size: Option[Long], mimeType: String): F[Unit] = self.uploadFile(name, stream, size, mimeType)
    def resetForm(id: Context.ElementId): F[Unit] = self.resetForm(id)
    def state: F[SN] = Effect[F].map(self.state)(read)
    def stateFocus[B](lens: Lens[SN, B]): F[B] = self.stateFocus(this.lens ++ lens)
    def transition(f: korolev.Transition[SN]): F[Unit] = self.transition(this.lens)(f)
    def transition[B](lens: Lens[SN, B])(f: Transition[B]): F[Unit] = self.transition(this.lens ++ lens)(f)
    def transitionAsync(f: TransitionAsync[F, SN]): F[Unit] = self.transitionAsync(this.lens)(f)
    def transitionAsync[B](lens: Lens[SN, B])(f: TransitionAsync[F, B]): F[Unit] = self.transitionAsync(this.lens ++ lens)(f)
    def transitionForce(f: Transition[SN]): F[Unit] = self.transitionForce(this.lens)(f)
    def transitionForce[B](lens: Lens[SN, B])(f: Transition[B]): F[Unit] = self.transitionForce(this.lens ++ lens)(f)
    def transitionForceAsync(f: TransitionAsync[F, SN]): F[Unit] = self.transitionForceAsync(this.lens)(f)
    def transitionForceAsync[B](lens: Lens[SN, B])(f: TransitionAsync[F, B]): F[Unit] = self.transitionForceAsync(this.lens ++ lens)(f)
    def sessionId: F[Qsid] = self.sessionId
    def evalJs(code: JsCode): F[String] = self.evalJs(code)
    def registerCallback(name: String)(f: String => F[Unit]): F[Unit] = self.registerCallback(name)(f)
  }

  sealed trait Binding[+F[_], +S, +M]

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
      AS: StateSerializer: StateDeserializer, M,
      CS: StateSerializer: StateDeserializer, P, E
    ](
      component: ComponentBase[F, CS, P, E],
      parameters: P,
      eventHandler: (Access[F, AS, M], E) => F[Unit]
    )
    extends Binding[F, AS, M] {

    def createInstance(node: Id,
                       sessionId: Qsid,
                       frontend: Frontend[F],
                       eventRegistry: EventRegistry[F],
                       stateManager: StateManager[F],
                       getRenderNum: () => Int,
                       stateQueue: Queue[F, (Id, Any, Option[Effect.Promise[Unit]])],
                       scheduler: Scheduler[F],
                       reporter: Reporter,
                       recovery: PartialFunction[Throwable, F[Unit]]): ComponentInstance[F, AS, M, CS, P, E] = {
      new ComponentInstance(
        node, sessionId, frontend, eventRegistry, stateManager, getRenderNum, component, stateQueue,
        createMiscProxy = (rc, k) => new StatefulRenderContext[Binding[F, CS, E]] {
          def currentContainerId: Id = rc.currentContainerId
          def currentId: Id = rc.currentId
          def subsequentId: Id = rc.subsequentId
          def openNode(xmlns: XmlNs, name: String): Unit = rc.openNode(xmlns, name)
          def closeNode(name: String): Unit = rc.closeNode(name)
          def setAttr(xmlNs: XmlNs, name: String, value: String): Unit = rc.setAttr(xmlNs, name, value)
          def setStyle(name: String, value: String): Unit = rc.setStyle(name, value)
          def addTextNode(text: String): Unit = rc.addTextNode(text)
          def addMisc(misc: Binding[F, CS, E]): Unit = k(this, misc)
        },
        scheduler, reporter, recovery
      )
    }
  }

  final case class Event[F[_], S, M](
      `type`: String,
      phase: EventPhase,
      stopPropagation: Boolean,
      effect: Access[F, S, M] => F[Unit]) extends Binding[F, S, M]

  final case class Delay[F[_], S, M](
      duration: FiniteDuration,
      effect: Access[F, S, M] => F[Unit]) extends Binding[F, S, M]

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
