package korolev.internal

import java.util.concurrent.atomic.AtomicInteger

import korolev.Context._
import korolev.Async.AsyncOps
import korolev.execution.Scheduler
import korolev._
import levsha.events.calculateEventPropagation
import levsha.impl.DiffRenderContext
import levsha.{Document, Id, XmlNs}
import slogging.LazyLogging

import scala.util.{Failure, Success}

final class ApplicationInstance[F[+ _]: Async: Scheduler, S, M](
    sessionId: QualifiedSessionId,
    connection: Connection[F],
    stateReader: StateReader,
    render: PartialFunction[S, Document.Node[Effect[F, S, M]]],
    router: Router[F, S, S],
    fromScratch: Boolean
) extends LazyLogging {

  private val devMode = new DevMode.ForRenderContext(sessionId.toString, fromScratch)
  private val currentRenderNum = new AtomicInteger(0)
  private val renderContext = DiffRenderContext[Effect[F, S, M]](savedBuffer = devMode.loadRenderContext())
  private val frontend = new ClientSideApi[F](connection)

  val topLevelComponentInstance = {
    val renderer = render.lift
    val eventRegistry = new EventRegistry[F](frontend)
    val initialState = stateReader.topLevel[S]
    val component = new Component[F, S, Any, M](initialState, Component.TopLevelComponentId) {
      def render(parameters: Any, state: S): Document.Node[Effect[F, S, M]] = {
        renderer(state).getOrElse {
          logger.error(s"Render is not defined for $state")
          Document.Node[Effect[F, S, M]] { rc =>
            rc.openNode(XmlNs.html, "body")
            rc.addTextNode("Render is not defined for the state")
            rc.closeNode("body")
          }
        }
      }
    }
    new ComponentInstance[F, S, M, S, Any, M](Id.TopLevel, sessionId, frontend, eventRegistry, component)
  }

  private def onState(giveStateReader: Boolean) = renderContext.synchronized {
    // Set page url if router exists
    router.fromState
      .lift(topLevelComponentInstance.getState)
      .foreach(path => frontend.changePageUrl(path))

    // Prepare render context
    renderContext.swap()

    // Reset all event handlers delays and elements
    topLevelComponentInstance.prepare()

    // Perform rendering
    topLevelComponentInstance.applyRenderContext(
      parameters = (), // Boxed unit as parameter. Top level component doesn't need parameters
      stateReaderOpt = if (giveStateReader) Some(stateReader) else None,
      rc = renderContext
    )

    // Infer changes
    frontend.startDomChanges()
    renderContext.diff(frontend)
    frontend.flushDomChanges()

    if (devMode.isActive)
      devMode.saveRenderContext(renderContext)

    // Make korolev ready to next render
    topLevelComponentInstance.dropObsoleteMisc()
    frontend.setRenderNum(currentRenderNum.incrementAndGet())
  }

  frontend.setHandlers(
    onHistory = { path =>
      val maybeState = router.toState.lift((topLevelComponentInstance.getState, path))
      maybeState foreach { asyncState =>
        asyncState run {
          case Success(newState) =>
            topLevelComponentInstance.setState(newState)
            onState(giveStateReader = false)
          case Failure(e) =>
            logger.error("Error occurred when updating state", e)
        }
      }
    },
    onEvent = { (renderNum, target, tpe) =>
      if (currentRenderNum.get == renderNum) {
        calculateEventPropagation(target, tpe) forall { eventId =>
          topLevelComponentInstance.applyEvent(eventId)
        }
        ()
      }
    },
    onFormDataProgress = topLevelComponentInstance.handleFormDataProgress
  )

  frontend.setRenderNum(0)

  // Content should be created from scratch
  // Remove all element from document.body
  if (fromScratch)
    frontend.cleanRoot()

  // If dev mode is enabled and active
  // CSS should be reloaded
  if (devMode.isActive)
    frontend.reloadCss()

  // Perform initial rendering
  if (fromScratch) {
    renderContext.openNode(levsha.XmlNs.html, "body")
    renderContext.closeNode("body")
    renderContext.diff(DiffRenderContext.DummyChangesPerformer)
  } else {

    if (devMode.hasSavedRenderContext) {
      renderContext.swap()
      topLevelComponentInstance.applyRenderContext((), renderContext, Some(stateReader))
      frontend.startDomChanges()
      renderContext.diff(frontend)
      frontend.flushDomChanges()
      devMode.saveRenderContext(renderContext)
    } else {

      topLevelComponentInstance.applyRenderContext((), renderContext, Some(stateReader))
      renderContext.diff(DiffRenderContext.DummyChangesPerformer)

      if (devMode.isActive)
        devMode.saveRenderContext(renderContext)
    }
  }

  topLevelComponentInstance.subscribeStateChange { (_, _) =>
    onState(giveStateReader = false)
  }

  if (fromScratch)
    onState(giveStateReader = true)
}
