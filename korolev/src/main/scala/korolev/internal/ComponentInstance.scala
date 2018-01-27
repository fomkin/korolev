package korolev.internal

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

import korolev._
import Context._
import Async._
import korolev.execution.Scheduler
import korolev.state.{StateDeserializer, StateManager, StateSerializer}
import levsha.Document.Node
import levsha.{Id, StatefulRenderContext, XmlNs}
import levsha.events.EventId
import slogging.LazyLogging

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

/**
  * Component state holder and effects performer
  *
  * Performing cycle:
  *
  * 1. prepare()
  * 2. Optionally setState()
  * 3. applyRenderContext()
  * 4. dropObsoleteMisc()
  */
final class ComponentInstance
  [
    F[+ _]: Async: Scheduler,
    AS: StateSerializer: StateDeserializer, M,
    CS: StateSerializer: StateDeserializer, P, E
  ](
    nodeId: Id,
    sessionId: QualifiedSessionId,
    frontend: ClientSideApi[F],
    eventRegistry: EventRegistry[F],
    stateManager: StateManager[F],
    getRenderNum: () => Int,
    val component: Component[F, CS, P, E]
  )
  extends LazyLogging { self =>

  import ComponentInstance._

  private val async = Async[F]
  private val miscLock = new Object()
  private val subscriptionsLock = new Object()
  private val lastFromId = new AtomicInteger(0)

  private val markedDelays = mutable.Set.empty[Id] // Set of the delays which are should survive
  private val markedComponentInstances = mutable.Set.empty[Id]
  private val delays = mutable.Map.empty[Id, DelayInstance[F, CS, E]]
  private val elements = mutable.Map.empty[ElementId[F, CS, E], Id]
  private val events = mutable.Map.empty[EventId, Event[F, CS, E]]
  private val nestedComponents = mutable.Map.empty[Id, ComponentInstance[F, CS, E, _, _, _]]
  private val formDataPromises = mutable.Map.empty[String, Promise[F, FormData]]
  private val formDataProgressTransitions = mutable.Map.empty[String, (Int, Int) => Transition[CS]]

  private val stateChangeSubscribers = mutable.ArrayBuffer.empty[(Id, Any) => Unit]
  private val pendingTransitions = new ConcurrentLinkedQueue[(Transition[CS], Promise[F, Unit])]()

  @volatile private var eventSubscription = Option.empty[E => _]
  @volatile private var transitionInProgress = false

  private object browserAccess extends Access[F, CS, E] {

    private def noElementException[T]: F[T] = {
      val exception = new Exception("No element matched for accessor")
      async.fromTry(Failure(exception))
    }

    private def getId(elementId: ElementId[F, CS, E]): F[Id] =
      elements
        .get(elementId)
        .fold(noElementException[Id])(id => async.pure(id))

    def property(elementId: ElementId[F, CS, E]): PropertyHandler[F] = {
      val idF = getId(elementId)
      new PropertyHandler[F] {
        def get(propName: Symbol): F[String] = idF flatMap { id =>
          frontend.extractProperty(id, propName.name)
        }

        def set(propName: Symbol, value: Any): F[Unit] = idF flatMap { id =>
          // XmlNs argument is empty cause it will be ignored
          frontend.setProperty(id, propName, value)
          async.unit
        }
      }
    }

    def focus(element: ElementId[F, CS, E]): F[Unit] =
      getId(element).flatMap { id =>
        frontend.focus(id)
        async.unit
      }

    def publish(message: E): F[Unit] = {
      eventSubscription.foreach(f => f(message))
      async.unit
    }

    def state: F[CS] = stateManager
      .read[CS](nodeId)
      .map(_.get)

    def sessionId: F[QualifiedSessionId] = async.pure(self.sessionId)

    def transition(f: Transition[CS]): F[Unit] = applyTransition(f)

    def downloadFormData(element: ElementId[F, CS, E]): FormDataDownloader[F, CS] = new FormDataDownloader[F, CS] {

      private val descriptor = nodeId.mkString + lastFromId.getAndIncrement()

      def start(): F[FormData] = getId(element) flatMap { id =>
        val promise = async.promise[FormData]
        frontend.uploadForm(id, descriptor)
        formDataPromises.put(descriptor, promise)
        promise.future
      }

      def onProgress(f: (Int, Int) => Transition[CS]): this.type = {
        formDataProgressTransitions.put(descriptor, f)
        this
      }
    }

    def evalJs(code: String): F[String] = frontend.evalJs(code)
    
    def eventData: F[String] = {
      frontend.extractEventData(getRenderNum())
    }
  }

  private def applyEventResult(effect: F[Unit]): Unit = {
    // Run effect
    effect.run {
      case Failure(e) => logger.error("Exception during applying transition", e)
      case Success(_) => ()
    }
  }

  private def createUnsubscribe[T](from: mutable.Buffer[T], that: T) = { () =>
    subscriptionsLock.synchronized { from -= that; () }
  }

  /**
    * Subscribe to component instance state changes.
    * Callback will be invoked for every state change.
    */
  def subscribeStateChange(callback: (Id, Any) => Unit): () => Unit = {
    subscriptionsLock.synchronized {
      stateChangeSubscribers += callback
      createUnsubscribe(stateChangeSubscribers, callback)
    }
  }

  /**
    * Subscribes to component instance events.
    * Callback will be invoked on call of `access.publish()` in the
    * component instance context.
    */
  def setEventsSubscription(callback: E => _): Unit = {
    subscriptionsLock.synchronized {
      eventSubscription = Some(callback)
    }
  }

  def applyRenderContext(parameters: P,
                         rc: StatefulRenderContext[Effect[F, AS, M]],
                         snapshot: StateManager.Snapshot): Unit = miscLock.synchronized {
    val state = snapshot[CS](nodeId).getOrElse(component.initialState)
    val node =
      try {
        component.render(parameters, state)
      } catch {
        case e: MatchError =>
          Node[Effect[F, CS, E]] { rc =>
            logger.error(s"Render is not defined for $state", e)
            rc.openNode(XmlNs.html, "span")
            rc.addTextNode("Render is not defined for the state")
            rc.closeNode("span")
          }
      }
    val proxy = new StatefulRenderContext[Effect[F, CS, E]] { proxy =>
      def currentId: Id = rc.currentId
      def currentContainerId: Id = rc.currentContainerId
      def openNode(xmlNs: XmlNs, name: String): Unit = rc.openNode(xmlNs, name)
      def closeNode(name: String): Unit = rc.closeNode(name)
      def setAttr(xmlNs: XmlNs, name: String, value: String): Unit = rc.setAttr(xmlNs, name, value)
      def addTextNode(text: String): Unit = rc.addTextNode(text)
      def addMisc(misc: Effect[F, CS, E]): Unit = {
        misc match {
          case event @ Event(eventType, phase, _) =>
            val id = rc.currentContainerId
            events.put(EventId(id, eventType.name, phase), event)
            eventRegistry.registerEventType(event.`type`)
          case element: ElementId[F, CS, E] =>
            val id = rc.currentContainerId
            elements.put(element, id)
            ()
          case delay: Delay[F, CS, E] =>
            val id = rc.currentContainerId
            markedDelays += id
            if (!delays.contains(id)) {
              val delayInstance = new DelayInstance(delay)
              delays.put(id, delayInstance)
              delayInstance.start(browserAccess)
            }
          case entry @ ComponentEntry(_, _: Any, _: ((Access[F, CS, E], Any) => F[Unit])) =>
            val id = rc.currentId
            nestedComponents.get(id) match {
              case Some(n: ComponentInstance[F, CS, E, Any, Any, Any]) if n.component.id == entry.component.id =>
                // Use nested component instance
                markedComponentInstances += id
                n.setEventsSubscription((e: Any) => entry.eventHandler(browserAccess, e).runIgnoreResult)
                n.applyRenderContext(entry.parameters, proxy, snapshot)
              case _ =>
                // Create new nested component instance
                val n = entry.createInstance(id, sessionId, frontend, eventRegistry, stateManager, getRenderNum)
                markedComponentInstances += id
                nestedComponents.put(id, n)
                n.subscribeStateChange { (id, state) =>
                  // Propagate nested component instance state change event
                  // to high-level component instance
                  stateChangeSubscribers.foreach { f =>
                    f(id, state)
                  }
                }
                n.setEventsSubscription((e: Any) => entry.eventHandler(browserAccess, e).runIgnoreResult)
                n.applyRenderContext(entry.parameters, proxy, snapshot)
            }
        }
      }
    }
    node(proxy)
  }

  def applyTransition(transition: Transition[CS]): F[Unit] = {
    def runTransition(transition: Transition[CS], promise: Promise[F, Unit]): Unit = {
      transitionInProgress = true
      stateManager.read[CS](nodeId) flatMap { maybeState =>
        val state = maybeState.getOrElse(component.initialState)
        try {
          val newState = transition(state)
          stateManager.write(nodeId, newState).map { _ =>
            stateChangeSubscribers.foreach(_.apply(nodeId, state))
          }
        } catch {
          case e: MatchError =>
            logger.warn("Transition doesn't fit the state", e)
            async.unit
          case e: Throwable =>
            logger.error("Exception happened when applying transition", e)
            async.unit
        }
      } runOrReport { _ =>
        promise.complete(Success(()))
        Option(pendingTransitions.poll()) match {
          case Some((t, p)) => runTransition(t, p)
          case None => transitionInProgress = false
        }
      }
    }

    val promise = async.promise[Unit]
    if (transitionInProgress) pendingTransitions.offer((transition, promise))
    else runTransition(transition, promise)
    promise.future
  }

  def applyEvent(eventId: EventId): Boolean = {
    events.get(eventId) match {
      case Some(event: Event[F, CS, E]) =>
        applyEventResult(event.effect(browserAccess))
        false
      case None =>
        nestedComponents.values.forall { nested =>
          nested.applyEvent(eventId)
        }
    }
  }

  /**
    * Remove all delays and nested component instances
    * which were not marked during applying render context.
    */
  def dropObsoleteMisc(): Unit = miscLock.synchronized {
    delays foreach {
      case (id, delay) =>
        if (!markedDelays.contains(id)) {
          delays.remove(id)
          delay.cancel()
        }
    }
    nestedComponents foreach {
      case (id, nested) =>
        if (!markedComponentInstances.contains(id)) nestedComponents.remove(id)
        else nested.dropObsoleteMisc()
    }
  }

  /**
    * Prepares component instance to applying render context.
    * Removes all temporary and obsolete misc.
    * All nested components also will be prepared.
    */
  def prepare(): Unit = miscLock.synchronized {
    markedComponentInstances.clear()
    markedDelays.clear()
    elements.clear()
    events.clear()
    // Remove only finished delays
    delays foreach {
      case (id, delay) =>
        if (delay.isFinished)
          delays.remove(id)
    }
    nestedComponents.values.foreach { nested =>
      nested.prepare()
    }
  }

  def resolveFormData(descriptor: String, formData: Try[FormData]): Unit = miscLock.synchronized {
    formDataPromises.get(descriptor) match {
      case Some(promise) =>
        promise.complete(formData)
        // Remove promise and onProgress handler
        // when formData loading is complete
        formDataProgressTransitions.remove(descriptor)
        formDataPromises.remove(descriptor)
        ()
      case None =>
        nestedComponents.values.foreach { nested =>
          nested.resolveFormData(descriptor, formData)
        }
    }
  }

  def handleFormDataProgress(descriptor: String, loaded: Int, total: Int): Unit = miscLock.synchronized {
    formDataProgressTransitions.get(descriptor) match {
      case None =>
        nestedComponents.values.foreach { nested =>
          nested.handleFormDataProgress(descriptor, loaded, total)
        }
      case Some(f) =>
        applyTransition(f(loaded.toInt, total.toInt))
          .runIgnoreResult
    }
  }
}

private object ComponentInstance {

  import Context.Access
  import Context.Delay

  final class DelayInstance[F[+ _]: Async: Scheduler, S, M](delay: Delay[F, S, M]) {

    @volatile private var handler = Option.empty[Scheduler.JobHandler[F, _]]
    @volatile private var finished = false

    def isFinished: Boolean = finished

    def cancel(): Unit = {
      handler.foreach(_.cancel())
    }

    def start(access: Access[F, S, M]): Unit = {
      handler = Some {
        Scheduler[F].scheduleOnce(delay.duration) {
          finished = true
          delay.effect(access).runIgnoreResult
        }
      }
    }
  }
}
