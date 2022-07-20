package korolev.engine

import korolev.Context.ComponentEntry
import korolev.Qsid
import korolev.context.{Binding, Component, KorolevApp, State}
import korolev.effect.{Effect, Hub, Queue, Reporter, Scheduler, Stream}
import korolev.effect.syntax.*
import korolev.internal.{DevMode, EventRegistry, Frontend}
import korolev.internal.Frontend.DomEventMessage
import korolev.state.StateManager
import korolev.web.PathAndQuery
import levsha.{Document, Id, StatefulRenderContext, XmlNs}
import levsha.events.{EventId, calculateEventPropagation}
import levsha.impl.DiffRenderContext
import levsha.impl.DiffRenderContext.ChangesPerformer

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable
import scala.concurrent.ExecutionContext

object SessionInstance {
  def apply[F[_] : Effect](session: Qsid, frontend: Frontend[F]) = {

  }
}

class SessionInstance[F[_] : Effect](session: Qsid,
                                     frontend: Frontend[F],
                                     stateManager: StateManager[F],
                                     app: KorolevApp[F],
                                     scheduler: Scheduler[F])(implicit reporter: Reporter) {
  application =>

  case class ComponentInstance(
    component: Component[F, _, _],
    render: State.Snapshot => Document.Node[Binding[F]]
  )

  private val devMode = new DevMode.ForRenderContext(session.toString)
  private val currentRenderNum = new AtomicInteger(0)
  private val stateQueue = Queue[F, (Id, Any)]()
  private val stateHub = Hub(stateQueue.stream)
  private val eventRegistry = new EventRegistry[F](frontend)

  private def onBinding(rc: StatefulRenderContext[Binding[F]], binding: Binding[F]): Unit = binding match {
    case event: Binding.Event[F] =>
      val id = rc.currentContainerId
      val eid = EventId(id, event.`type`, event.phase)
      val es = events.getOrElseUpdate(eid, Vector.empty)
      events.put(eid, es :+ event)
      eventRegistry.registerEventType(event.`type`)
    case element: Binding.Element =>
      val id = rc.currentContainerId
      elements.put(element, id)
      ()
    case entry: Binding.ComponentEntry[F, _, _] =>
      val id = rc.subsequentId
      componentInstances.get(id) match {
        // The component instance initializing in progress
        case Some(None) => markedComponentInstances += id
        // The component instance initialized
        case Some(Some(instance)) if instance.component.name == entry.component.name =>
          markedComponentInstances += id
          instance.render(null)(rc)
        case _ =>
          val
          markedComponentInstances += id
          nestedComponents.put(id, n)
          n.unsafeInitialize()
          n.setEventsSubscription((e: Any) => entry.eventHandler(browserAccess, e).runAsyncForget)
          n.applyRenderContext(entry.parameters, proxy, snapshot)
      }


  }

  private val renderContext: StatefulRenderContext[Binding[F]] = new StatefulRenderContext[Binding[F]] {
    val underlying: DiffRenderContext[Binding[F]] = DiffRenderContext[Binding[F]](savedBuffer = devMode.loadRenderContext())

    def currentContainerId: Id = underlying.currentContainerId

    def currentId: Id = underlying.currentId

    def subsequentId: Id = underlying.subsequentId

    def openNode(xmlns: XmlNs, name: String): Unit = underlying.openNode(xmlns, name)

    def closeNode(name: String): Unit = underlying.closeNode(name)

    def setAttr(xmlNs: XmlNs, name: String, value: String): Unit = underlying.setAttr(xmlNs, name, value)

    def setStyle(name: String, value: String): Unit = underlying.setStyle(name, value)

    def addTextNode(text: String): Unit = underlying.addTextNode(text)

    def addMisc(misc: Binding[F]): Unit = onBinding(this, misc)
  }

  //    val topLevelComponentInstance: ComponentInstance[F, S, M, S, Any, M] = {
  //      val component = new Component[F, S, Any, M](initialState, Component.TopLevelComponentId) {
  //        def render(parameters: Any, state: S): Document.Node[Binding[F, S, M]] = {
  //          try {
  //            application.render(state)
  //          } catch {
  //            case e: MatchError =>
  //              Document.Node[Binding[F, S, M]] { rc =>
  //                reporter.error(s"Render is not defined for $state")
  //                rc.openNode(XmlNs.html, "body")
  //                rc.addTextNode("Render is not defined for the state. ")
  //                rc.addTextNode(e.getMessage())
  //                rc.closeNode("body")
  //              }
  //          }
  //        }
  //      }
  //      val componentInstance = new ComponentInstance[F, S, M, S, Any, M](
  //        Id.TopLevel, sessionId, frontend, eventRegistry,
  //        stateManager, () => currentRenderNum.get(), component,
  //        stateQueue, createMiscProxy, scheduler, reporter
  //      )
  //      componentInstance.setEventsSubscription(messagesQueue.offerUnsafe)
  //      componentInstance
  //    }
  //

  /**
   * If dev mode is enabled save render context
   */
  private def saveRenderContextIfNecessary(): F[Unit] =
    if (devMode.isActive) Effect[F].delay(devMode.saveRenderContext(renderContext))
    else Effect[F].unit

  private def nextRenderNum(): F[Int] =
    Effect[F].delay(currentRenderNum.incrementAndGet())

  def applyRenderContext(): Unit = miscLock.synchronized {
    // Reset all event handlers delays and elements
    prepare()
    val node =
      try {
        app.render()
      } catch {
        case e: MatchError =>
          Node[Binding[F, CS, E]] { rc =>
            reporter.error(s"Render is not defined for $state", e)
            rc.openNode(XmlNs.html, "span")
            rc.addTextNode("Render is not defined for the state")
            rc.closeNode("span")
          }
      }
    val proxy = createMiscProxy(rc, { (proxy, misc) =>
      misc match {
        case event: Event[F, CS, E] =>
          val id = rc.currentContainerId
          val eid = EventId(id, event.`type`, event.phase)
          val es = events.getOrElseUpdate(eid, Vector.empty)
          events.put(eid, es :+ event)
          eventRegistry.registerEventType(event.`type`)
        case element: ElementId =>
          val id = rc.currentContainerId
          elements.put(element, id)
          ()
        case delay: Delay[F, CS, E] =>
          val id = rc.currentContainerId
          markedDelays += id
          if (!delays.contains(id)) {
            val delayInstance = new DelayInstance(delay, scheduler, reporter)
            delays.put(id, delayInstance)
            delayInstance.start(browserAccess)
          }
        case entry: ComponentEntry[F, CS, E, Any, Any, Any] =>
          val id = rc.subsequentId
          nestedComponents.get(id) match {
            case Some(n: ComponentInstance[F, CS, E, Any, Any, Any]) if n.component.id == entry.component.id =>
              // Use nested component instance
              markedComponentInstances += id
              n.setEventsSubscription((e: Any) => entry.eventHandler(browserAccess, e).runAsyncForget)
              n.applyRenderContext(entry.parameters, proxy, snapshot)
            case _ =>
              val n = entry.createInstance(
                id, session, frontend, eventRegistry,
                stateManager, getRenderNum, stateQueue,
                scheduler, reporter
              )
              markedComponentInstances += id
              nestedComponents.put(id, n)
              n.unsafeInitialize()
              n.setEventsSubscription((e: Any) => entry.eventHandler(browserAccess, e).runAsyncForget)
              n.applyRenderContext(entry.parameters, proxy, snapshot)
          }
      }
    })
    node(proxy)
  }

  private def onState(): F[Unit] =
    for {
      snapshot <- stateManager.snapshot
      // Set page url if router exists
      //      _ <-  router.fromState
      //        .lift(snapshot(Id.TopLevel).getOrElse(initialState))
      //        .fold(Effect[F].unit)(uri => frontend.changePageUrl(uri))
      _ <- Effect[F].delay {
        // Prepare render context
        renderContext.swap()
        // Perform rendering
        topLevelComponentInstance.applyRenderContext(
          parameters = (), // Boxed unit as parameter. Top level component doesn't need parameters
          snapshot = snapshot,
          rc = renderContext
        )
      }
      // Infer and perform changes
      _ <- frontend.performDomChanges(renderContext.diff)
      _ <- saveRenderContextIfNecessary()
      // Make korolev ready to next render
      renderNum <- nextRenderNum()
      _ <- Effect[F].delay(topLevelComponentInstance.dropObsoleteMisc())
      _ <- frontend.setRenderNum(renderNum)
    } yield ()


  private def onHistory(pq: PathAndQuery): F[Unit] =
    stateManager
      .read[S](Id.TopLevel)
      .flatMap { maybeTopLevelState =>
        router
          .toState
          .lift(pq)
          .fold(Effect[F].delay(Option.empty[S]))(_ (maybeTopLevelState.getOrElse(initialState)).map(Some(_)))
      }
      .flatMap {
        case Some(newState) =>
          stateManager
            .write(Id.TopLevel, newState)
            .after(stateQueue.enqueue(Id.TopLevel, newState))
        case None =>
          Effect[F].unit
      }

  private def onEvent(dem: DomEventMessage): F[Unit] =
    Effect[F].delay {
      if (currentRenderNum.get == dem.renderNum) {
        calculateEventPropagation(dem.target, dem.eventType) forall { eventId =>
          topLevelComponentInstance.applyEvent(eventId)
        }
        ()
      }
    }

  frontend
    .browserHistoryMessages
    .foreach(onHistory)
    .runAsyncForget

  frontend
    .domEventMessages
    .foreach(onEvent)
    .runAsyncForget

  stateHub
    .newStream()
    .flatMap(_.foreach(_ => onState()))
    .runAsyncForget

  val stateStream: F[Stream[F, (Id, Any)]] =
    stateHub.newStream()

  val messagesStream: Stream[F, M] =
    messagesQueue.stream

  def destroy(): F[Unit] = for {
    _ <- stateQueue.close()
    _ <- messagesQueue.close()
    _ <- topLevelComponentInstance.destroy()
  } yield ()

  def initialize()(implicit ec: ExecutionContext): F[Unit] = {

    // If dev mode is enabled and active
    // CSS should be reloaded
    def reloadCssIfNecessary() =
      if (devMode.isActive) frontend.reloadCss()
      else Effect[F].unit

    // Render current state using 'performDiff'.
    def render(performDiff: (ChangesPerformer => Unit) => F[Unit]) =
      for {
        snapshot <- stateManager.snapshot
        _ <- Effect[F].delay(topLevelComponentInstance.applyRenderContext((), renderContext, snapshot))
        _ <- performDiff(renderContext.diff)
        _ <- saveRenderContextIfNecessary()
      } yield ()

    if (devMode.saved) {

      // Initialize with
      // 1. Old page in users browser
      // 2. Has saved render context
      for {
        _ <- frontend.setRenderNum(0)
        _ <- reloadCssIfNecessary()
        _ <- Effect[F].delay(renderContext.swap())
        // Serialized render context exists.
        // It means that user is looking at page
        // generated by old code. The code may
        // consist changes in render, so we
        // should deliver them to the user.
        _ <- render(frontend.performDomChanges)
        _ <- topLevelComponentInstance.initialize()
      } yield ()

    } else {

      // Initialize with pre-rendered page
      for {
        _ <- frontend.setRenderNum(0)
        _ <- reloadCssIfNecessary()
        _ <- render(f => Effect[F].delay(f(DiffRenderContext.DummyChangesPerformer)))
        _ <- topLevelComponentInstance.initialize()
      } yield ()
    }
  }
}