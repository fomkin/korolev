package korolev.context

import korolev.Qsid
import korolev.data.Bytes
import korolev.effect.{Reporter, Stream}
import korolev.server.{HttpRequest, HttpResponse, StateLoader}
import korolev.state.IdGenerator
import korolev.util.JsCode
import korolev.web.{FormData, MimeTypes}
import levsha.Document
import levsha.events.EventPhase

import scala.concurrent.duration.{DurationInt, FiniteDuration}

case class KorolevApp[F[_]](render: State.Snapshot => Document.Node[Binding[F]],
                            stateChange: State.Snapshot => F[Unit],
                            closeHandler: () => F[Unit])

case class KorolevConfig[F[_]](stateLoader: StateLoader[F, Seq[State.Entry[_]]],
                               http: PartialFunction[HttpRequest[F], F[HttpResponse[F]]] =
                                 PartialFunction.empty[HttpRequest[F], F[HttpResponse[F]]],
                               document: State.Snapshot => Document.Node[Binding[F]],
                               connectionLostWidget: Document.Node[Binding[F]],
                               rootPath: String = "/",
                               maxFormDataEntrySize: Int = 1024 * 8,
                               idGenerator: IdGenerator[F] = IdGenerator.default[F](),
                               heartbeatInterval: FiniteDuration = 5.seconds,
                               reporter: Reporter = Reporter.PrintReporter)

trait Context[F[_]] {

  def maybeCii: Option[CII]

  def event(`type`: String, phase: EventPhase = EventPhase.Bubbling, stopPropagation: Boolean = false)(
      effect: => F[Unit]): Binding.Event[F] =
    Binding.Event(`type`, phase, stopPropagation, maybeCii, () => effect)

  // Component

  final class ComponentApply[P, E](component: Component[F, P, E], parameters: P) {
    def apply(): Binding.ComponentEntry[F, P, E] =
      Binding.ComponentEntry[F, P, E](component, parameters, maybeCii, None)

    def apply(eventHandler: E => F[Unit]): Binding.ComponentEntry[F, P, E] =
      Binding.ComponentEntry[F, P, E](component, parameters, maybeCii, Some(eventHandler))
  }

  implicit final class ComponentDsl[P, E](val component: Component[F, P, E]) {
    def apply(parameters: P): ComponentApply[P, E] = new ComponentApply(component, parameters)
  }

  implicit def componentApplyToBinding[P, E](ca: ComponentApply[P, E]): Binding[F] = ca()
}

final class ApplicationContext[F[_]](val access: Access[F], val session: Qsid) extends Context[F] {
  val maybeCii: Option[CII] = None
}

sealed trait Binding[+F[_]]

object Binding {

  final case class Element(name: String) extends AnyVal with Binding[Nothing]

  final case class Event[F[_]](`type`: String,
                               phase: EventPhase,
                               stopPropagation: Boolean,
                               maybeCii: Option[CII],
                               effect: () => F[Unit])
      extends Binding[F]

  final case class ComponentEntry[F[_], P, E](component: Component[F, P, E],
                                              parameters: P,
                                              maybeEnclosingCii: Option[CII],
                                              eventHandler: Option[E => F[Unit]])
      extends Binding[F]
}

object State {

  case class Entry[T](of: State[T], value: T)

  sealed trait UpdatePolicy

  object UpdatePolicy {
    case object JustUpdate extends UpdatePolicy

    case object CompareAndSet extends UpdatePolicy

    case object CompareAndSetRetry extends UpdatePolicy
  }

  trait Snapshot {
    def take[T](state: State[T]): T
  }
}

case class State[T](empty: T) {
  self =>

  import State.UpdatePolicy

  final class WithAccess[F[_]](access: Access[F]) {
    def get: F[T] =
      access.getState(self)

    def set(value: T): F[Unit] =
      access.setState(self, value)

    def update(f: T => T): F[Unit] =
      access.updateState(self)(f)

    def updateAsync(policy: UpdatePolicy)(f: T => F[T]): F[Boolean] =
      access.updateStateAsync(self, policy)(f)

    def updateAsync(f: T => F[T]): F[Boolean] =
      access.updateStateAsync(self, UpdatePolicy.JustUpdate)(f)
  }

  def withAccess[F[_]](access: Access[F]): WithAccess[F] = new WithAccess(access)
}

object Access {

  sealed trait FileHandler {
    def fileName: String

    def size: Long

    def element: Binding.Element
  }

  private[korolev] case class FileHandlerImpl(fileName: String, size: Long, element: Binding.Element)
      extends FileHandler

  sealed trait AccessException extends Throwable

  object AccessException {
    class ElementBindingException(val element: Binding.Element)
        extends Exception(s"No DOM element corresponds to '${element.name}' element")
        with AccessException
  }
}

trait Access[F[_]] {

  import Access.FileHandler
  import Binding.Element
  import State.UpdatePolicy

  def getState[T](state: State[T]): F[T]

  // TODO awaitRender
  def setState[T](state: State[T], value: T): F[Unit]

  // TODO awaitRender
  def updateState[T](state: State[T])(f: T => T): F[Unit]

  // TODO awaitRender
  def updateStateAsync[T](state: State[T], policy: UpdatePolicy = UpdatePolicy.JustUpdate)(f: T => F[T]): F[Boolean]

  def setProperty(element: Element, propertyName: String, value: String): F[Unit]

  def getProperty(element: Element, propertyName: String): F[String]

  def setValue(element: Element, value: String): F[Unit]

  def getValue(element: Element): F[String]

  /**
    * Set focus on the element in the browser
    */
  def focus(id: Element): F[Unit]

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
    *
    * @param id form elementId
    * @return
    */
  def downloadFormData(id: Element): F[FormData]

  /**
    * Download the selected file list from input appropriate
    * to given element id. Use this method carefully because
    * all files are saving to RAM.
    */
  def downloadFiles(id: Element): F[List[(FileHandler, Bytes)]]

  /**
    * Same as [[downloadFiles]] but for stream mode. The method is useful
    * when user want to upload very large files list which is problematic
    * to keep in memory (especially when count of users is more than one).
    */
  def downloadFilesAsStream(id: Element): F[List[(FileHandler, Stream[F, Bytes])]]

  /**
    * Download file from the client side by file handler
    */
  def downloadFileAsStream(handler: FileHandler): F[Stream[F, Bytes]]

  /**
    * Get only file list for input
    */
  def listFiles(id: Element): F[List[FileHandler]]

  /**
    * Upload stream to the client side with selected name, size and mimeType
    */
  def uploadFile(name: String,
                 stream: Stream[F, Bytes],
                 size: Option[Long],
                 mimeType: String = MimeTypes.`application/octet-stream`): F[Unit]

  /**
    * Purge inputs in given form.
    *
    * @param id form element id binding
    */
  def resetForm(id: Element): F[Unit]

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

  def eventData: F[String]
}

object Component {

  trait ComponentAccess[F[_], E] extends Access[F] {
    def dispatchEvent(event: E): F[Unit]
  }

  final class ComponentContext[F[_], E](val access: ComponentAccess[F, E], val maybeCii: Option[CII]) extends Context[F]

  final case class ComponentInstance[F[_], P](cii: CII,
                                              render: (P, State.Snapshot) => Document.Node[Binding[F]],
                                              stateChange: State.Snapshot => F[Unit],
                                              closeHandler: () => F[Unit])
}

trait Component[F[_], P, E] {

  import Component._

  def name: String

  def init(f: ComponentContext[F, E] => ComponentInstance[F, P]): F[Unit]
}
