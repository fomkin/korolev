package korolev.internal

import java.util.concurrent.atomic.AtomicInteger

import bridge.JSAccess
import korolev.ApplicationContext._
import korolev.Async.AsyncOps
import korolev.{Async, Component, Router, StateReader}
import levsha.events.calculateEventPropagation
import levsha.impl.DiffRenderContext
import levsha.{Document, Id, XmlNs}
import slogging.LazyLogging

import scala.util.{Failure, Success}

final class ApplicationInstance[F[+ _]: Async, S, M](
    identifier: String,
    jsAccess: JSAccess[F],
    stateReader: StateReader,
    render: PartialFunction[S, Document.Node[Effect[F, S, M]]],
    router: Router[F, S, S],
    fromScratch: Boolean
) extends LazyLogging {

  private val devMode = new DevMode.ForRenderContext(identifier, fromScratch)
  private val currentRenderNum = new AtomicInteger(0)
  private val renderContext = DiffRenderContext[Effect[F, S, M]](savedBuffer = devMode.loadRenderContext())
  private val frontend = new ClientSideApi[F](jsAccess)

  val topLevelComponentInstance = {
    val renderer = render.lift
    val eventRegistry = new EventRegistry[F](frontend)
    val initialState = stateReader.topLevel[S]
    val component = new Component[F, S, Any, M](initialState, Component.TopLevelComponentId) {
      def render(parameters: Any, state: S): Document.Node[Effect[F, S, M]] = {
        renderer(state).getOrElse {
          Document.Node[Effect[F, S, M]] { rc =>
            // TODO better reporting
            // TODO should be safe!
            rc.openNode(XmlNs.html, "body")
            rc.addTextNode("Render didn't defined for this state")
            rc.closeNode("body")
          }
        }
      }
    }
    new ComponentInstance[F, S, M, S, Any, M](Id.TopLevel, frontend, eventRegistry, component)
  }

  private val initialization = frontend.initialize(
    onHistory = { pathString =>
      val path = Router.Path.fromString(pathString)
      val maybeState = router.toState.lift((topLevelComponentInstance.getState, path))
      maybeState foreach { asyncState =>
        asyncState run {
          case Success(newState) =>
            topLevelComponentInstance.setState(newState)
          case Failure(e) =>
            logger.error("Error occurred when updating state", e)
        }
      }
    },
    onEvent = { targetAndType =>
      val Array(renderNum, target, tpe) = targetAndType.split(':')
      if (currentRenderNum.get == renderNum.toInt) {
        calculateEventPropagation(Id(target), tpe) forall { eventId =>
          topLevelComponentInstance.applyEvent(eventId)
        }
        ()
      }
    },
    onFormDataProgress = { descriptorLoadedTotal =>
      val Array(descriptor, loaded, total) = descriptorLoadedTotal.split(':')
      topLevelComponentInstance.handleFormDataProgress(descriptor, loaded.toInt, total.toInt)
    },
    doCleanRoot = fromScratch,
    doReloadCss = devMode.isActive
  )

  private def onState(giveStateReader: Boolean) = renderContext.synchronized {
    // Set page url if router exists
    router.fromState
      .lift(topLevelComponentInstance.getState)
      .foreach(path => frontend.changePageUrl(path).runIgnoreResult)

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
    renderContext.diff(frontend)
    if (devMode.isActive) devMode.saveRenderContext(renderContext)

    // Make korolev ready to next render
    topLevelComponentInstance.dropObsoleteMisc()
    frontend.setRenderNum(currentRenderNum.incrementAndGet(), doFlush = false).runIgnoreResult

    // Flush all commands as one packet
    jsAccess.flush()
  }

  initialization run {
    case Success(_) =>
      // Perform initial rendering
      if (fromScratch) {
        renderContext.openNode(levsha.XmlNs.html, "body")
        renderContext.closeNode("body")
        renderContext.diff(DiffRenderContext.DummyChangesPerformer)
      } else {
        if (devMode.hasSavedRenderContext) {
          renderContext.swap()
          topLevelComponentInstance.applyRenderContext((), renderContext, Some(stateReader))
          renderContext.diff(frontend)
          devMode.saveRenderContext(renderContext)
        } else {
          topLevelComponentInstance.applyRenderContext((), renderContext, Some(stateReader))
          renderContext.diff(DiffRenderContext.DummyChangesPerformer)
          if (devMode.isActive) devMode.saveRenderContext(renderContext)
        }
      }

      topLevelComponentInstance.subscribeStateChange((_, _) => onState(giveStateReader = false))
      if (fromScratch) onState(giveStateReader = true)
      else jsAccess.flush()
    case Failure(e) =>
      logger.error("Error occurred on event callback registration", e)
  }
}
