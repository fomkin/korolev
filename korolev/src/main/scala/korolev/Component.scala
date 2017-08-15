package korolev

import java.util.UUID

import korolev.ApplicationContext._
import korolev.Async._
import korolev.StateManager.Transition
import korolev.util.AtomicReference
import levsha.Document.Node
import levsha.events.EventId
import levsha.{Id, StatefulRenderContext, XmlNs}

import scala.collection.mutable
import scala.util.{Failure, Random}

sealed abstract class Component[F[+ _]: Async, CS, E] {

  val id = UUID.randomUUID()

  def tag: String = "div"

  def xmlNs: XmlNs = XmlNs.html

  val context = ApplicationContext[F, CS, E](Async[F], null) // TODO ERROR

  def apply[AS, M](initialState: CS)(f: E => EventResult[F, AS]): ComponentEntry[F, AS, M, CS, E] =
    ComponentEntry(this, initialState)

  def render(state: CS): Node[Effect[F, CS, E]]
}

object Component {

  def apply[F[+ _]: Async, S, E](
      renderFunction: (ApplicationContext[F, S, E], S) => Node[Effect[F, S, E]]): Component[F, S, E] = {
    new ComponentFunction(renderFunction)
  }

  /** A way to define components in functional style */
  private class ComponentFunction[F[+ _]: Async, S, E](
      renderFunction: (ApplicationContext[F, S, E], S) => Node[Effect[F, S, E]])
      extends Component[F, S, E] {

    def render(state: S): Node[Effect[F, S, E]] = {
      renderFunction(context, state)
    }
  }

  /**
    * Typed interface to client side
    */
  abstract class Frontend[F[+ _]: Async] {
    // client.call("ListenEvent", `type`.name, false)
    def listenEvent(name: String, preventDefault: Boolean): F[Unit]

    // client.call[Unit]("UploadForm", id.mkString, descriptor)
    def uploadForm(id: Id, descriptor: String): F[Unit]

    // client.callAndFlush[Unit]("Focus", id.mkString)
    def focus(id: Id): F[Unit]

    // client.callAndFlush("SetAttr", id.mkString, "", propName.name, value, true)
    def setAttr[T](id: Id, xmlNs: String, name: String, value: T, isProperty: Boolean): F[Unit]

    // client.callAndFlush("ExtractProperty", id.mkString, propName.name)
    def extractProperty[T](id: Id, name: String): F[T]
  }

  /**
    * Save information about what type of events are already
    * listening on the client
    */
  final class EventRegistry[F[+ _]: Async](frontend: Frontend[F]) {

    private val knownEventTypes = mutable.Set('submit)

    /**
      * Notifies client side that he should listen
      * all events of the type. If event already listening
      * on the client side, client will be not notified again.
      */
    def registerEventType(`type`: Symbol): Unit = {
      if (!knownEventTypes.contains(`type`)) {
        frontend
          .listenEvent(`type`.name, preventDefault = false)
          .runIgnoreResult
      }
    }
  }

  /**
    * Component state holder and effects performer
    */
  final class ComponentInstance[F[+ _]: Async, AS, M, CS, E](initialState: CS,
                                                             frontend: Frontend[F],
                                                             eventRegistry: EventRegistry[F],
                                                             val component: Component[F, CS, E]) {

    private val async = Async[F]
    private val state = AtomicReference(initialState)
    private val lastSetState = AtomicReference(initialState)
    private val markedDelays = mutable.Set.empty[Id] // Set of the delays which are should survive
    private val elements = mutable.Map.empty[ApplicationContext.ElementId[F, CS, E], Id]
    private val events = mutable.Map.empty[EventId, ApplicationContext.Event[F, CS, E]]
    private val delays = mutable.Map.empty[Id, ApplicationContext.Delay[F, CS, E]]
    private val nestedComponents = mutable.Map.empty[Id, ComponentInstance[F, CS, E, _, _]]
    private val formDataPromises = mutable.Map.empty[String, Promise[F, FormData]]
    private val formDataProgressTransitions = mutable.Map.empty[String, (Int, Int) => Transition[CS]]
    private val stateChangeSubscribers = mutable.ArrayBuffer.empty[() => Unit]
    private val eventSubscribers = mutable.ArrayBuffer.empty[E => Unit]

    private object browserAccess extends Access[F, CS, E] {

      private def noElementException[T]: F[T] = {
        val exception = new Exception("No element matched for accessor")
        async.fromTry(Failure(exception))
      }

      private def getId(elementId: ElementId[F, CS, E]): F[Id] =
        elements
          .get(elementId)
          .fold(noElementException[Id])(id => async.pure(id))

      def property[T](elementId: ElementId[F, CS, E]): PropertyHandler[F, T] = {
        val idF = getId(elementId)
        new PropertyHandler[F, T] {
          def get(propName: Symbol): F[T] = idF flatMap { id =>
            frontend.extractProperty(id, propName.name)
          }

          def set(propName: Symbol, value: T): F[Unit] = idF flatMap { id =>
            // XmlNs argument is empty cause it will be ignored
            frontend.setAttr(id, "", propName.name, value, isProperty = true)
          }
        }
      }

      def property[T](element: ElementId[F, CS, E], propName: Symbol): F[T] =
        property[T](element).get(propName)

      def focus(element: ElementId[F, CS, E]): F[Unit] =
        getId(element).flatMap(id => frontend.focus(id))

      def publish(message: E): F[Unit] = {
        eventSubscribers.foreach(_(message))
        async.unit
      }

      def downloadFormData(element: ElementId[F, CS, E]): FormDataDownloader[F, CS] = new FormDataDownloader[F, CS] {
        val descriptor = Random.alphanumeric.take(5).mkString

        def start(): F[FormData] = getId(element) flatMap { id =>
          val promise = async.promise[FormData]
          val future = frontend.uploadForm(id, descriptor)
          formDataPromises.put(descriptor, promise)
          future.flatMap(_ => promise.future)
        }

        def onProgress(f: (Int, Int) => Transition[CS]): this.type = {
          formDataProgressTransitions.put(descriptor, f)
          this
        }
      }
    }

    /**
      * Subscribe to component instance state changes.
      * Callback will be invoked for every state change.
      */
    def subscribeStateChange(callback: () => Unit): () => Unit = {
      stateChangeSubscribers += callback
      () => { stateChangeSubscribers -= callback; () }
    }

    /**
      * TODO
      */
    def subscribeEvents(callback: E => Unit): () => Unit = {
      eventSubscribers += callback
      () => { eventSubscribers -= callback; () }
    }

    /**
      * Set state of the component from the outside. For example
      * from a top level component. Component instance remember
      * last [[setState]] value and don't update current state if
      * new state and last setState value is equals.
      */
    def setState(newState: CS): CS = {
      state.transform { _ =>
        lastSetState.transform { last =>
          if (last != newState) newState
          else last
        }
      }
    }

    /**
      * Type-unsafe version of setState
      */
    def setStateUnsafe(newState: Any): CS = {
      setState(newState.asInstanceOf[CS])
    }

    /**
      * TODO doc
      */
    def applyRenderContext(rc: StatefulRenderContext[Effect[F, AS, M]]): Unit = {
      val node = component.render(state())
      val proxy = new StatefulRenderContext[Effect[F, CS, E]] { proxy =>
        def currentId: Id = rc.currentId
        def openNode(xmlNs: XmlNs, name: String): Unit = rc.openNode(xmlNs, name)
        def closeNode(name: String): Unit = rc.closeNode(name)
        def setAttr(xmlNs: XmlNs, name: String, value: String): Unit = rc.setAttr(xmlNs, name, value)
        def addTextNode(text: String): Unit = rc.addTextNode(text)
        def addMisc(misc: Effect[F, CS, E]): Unit = {
          misc match {
            case event: ApplicationContext.Event[F, CS, E] =>
              val id = rc.currentId
              events.put(EventId(id, event.`type`.name, event.phase), event)
              eventRegistry.registerEventType(event.`type`)
            case element: ApplicationContext.ElementId[F, CS, E] =>
              val id = rc.currentId
              elements.put(element, id)
              ()
            case delay: ApplicationContext.Delay[F, CS, E] =>
              val id = rc.currentId
              markedDelays += id
              if (!delays.contains(id)) {
                delays.put(id, delay)
                //onStartDelay(delay)
                // TODO start delay
              }
              ()
            case entry @ ApplicationContext.ComponentEntry(value, newState) =>
              rc.openNode(value.xmlNs, value.tag)
              val id = rc.currentId
              nestedComponents.get(id) match {
                case Some(nested) if nested.component.tag == value.tag && nested.component.xmlNs == value.xmlNs =>
                  nested.setStateUnsafe(newState)
                  nested.applyRenderContext(proxy)
                case _ =>
                  val nested = entry.createInstance(frontend, eventRegistry)
                  nestedComponents.put(id, nested)
                  nested.applyRenderContext(proxy.asInstanceOf)
              }
              rc.closeNode(value.tag)
          }
        }
      }
      node(proxy)
    }
  }

}

//
//object Demo {
//
//  import korolev.execution._
//
//  val myComponent = Component[Future, String, String] { (ctx, state) =>
//    import ctx._
//    import symbolDsl._
//
//    'button (
//      state,
//      eventWithAccess('click) { access =>
//        access.publish("click happened")
//        noTransition
//      }
//    )
//  }
//
//  val ac = ApplicationContext[Future, Int, Any]
//
//  import ac._
//  import symbolDsl._
//
//  'form (
//    'input ('type /= "text"),
//    myComponent[Int, Any]("Ok") {
//      case "click happened" =>
//        immediateTransition {
//          case n => n + 1
//        }
//    }
//  )
//}
