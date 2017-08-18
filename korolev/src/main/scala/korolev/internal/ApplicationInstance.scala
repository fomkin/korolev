package korolev.internal

import java.util.concurrent.atomic.AtomicInteger

import bridge.JSAccess
import korolev.ApplicationContext._
import korolev.Async.AsyncOps
import korolev.{Async, Component, Router, StateReader}
import levsha.events.calculateEventPropagation
import levsha.impl.DiffRenderContext
import levsha.impl.DiffRenderContext.ChangesPerformer
import levsha.{Document, Id, XmlNs}
import slogging.LazyLogging

import scala.util.{Failure, Success}

final class ApplicationInstance[F[+ _]: Async, S, M](
    identifier: String,
    jsAccess: JSAccess[F],
    topLevelInitialState: S,
    stateReaderOpt: Option[StateReader],
    render: PartialFunction[S, Document.Node[Effect[F, S, M]]],
    router: Router[F, S, S],
    fromScratch: Boolean
) extends LazyLogging {

  private val async = Async[F]
  private val devMode = new DevMode.ForRenderContext(identifier, fromScratch)
  private val client = {
    jsAccess.global.getAndSaveAs("Korolev", "@Korolev").runIgnoreResult
    jsAccess.obj("@Korolev")
  }
  
  private val renderContext = DiffRenderContext[Effect[F, S, M]](savedBuffer = devMode.loadRenderContext())
  private val currentRenderNum = new AtomicInteger(0)
  private val changesPerformer = new ChangesPerformer {
    private def isProp(name: String) = name.charAt(0) == '^'

    private def escapeName(name: String, isProp: Boolean) =
      if (isProp) name.substring(1)
      else name

    def remove(id: Id): Unit =
      client.call("Remove", id.parent.get.mkString, id.mkString).runIgnoreResult()

    def createText(id: Id, text: String): Unit =
      client.call("CreateText", id.parent.get.mkString, id.mkString, text).runIgnoreResult()

    def create(id: Id, xmlNs: String, tag: String): Unit = {
      val parent = id.parent.fold("0")(_.mkString)
      val pXmlns =
        if (xmlNs eq levsha.XmlNs.html.uri) 0
        else xmlNs
      client.call("Create", parent, id.mkString, pXmlns, tag).runIgnoreResult()
    }

    def setAttr(id: Id, xmlNs: String, name: String, value: String): Unit = {
      val p = isProp(name)
      val n = escapeName(name, p)
      val pXmlns =
        if (xmlNs eq levsha.XmlNs.html.uri) 0
        else xmlNs
      client.call("SetAttr", id.mkString, pXmlns, n, value, p).runIgnoreResult()
    }

    def removeAttr(id: Id, xmlNs: String, name: String): Unit = {
      val p = isProp(name)
      val n = escapeName(name, p)
      val pXmlns =
        if (xmlNs eq levsha.XmlNs.html.uri) 0
        else xmlNs
      client.call("RemoveAttr", id.mkString, pXmlns, n, p).runIgnoreResult()
    }
  }

  val topLevelComponentInstance = {
    val renderer = render.lift
    val frontend = new Frontend[F](client)
    val eventRegistry = new EventRegistry[F](frontend)
    val initialState = stateReaderOpt.flatMap(_.read[S](Id.TopLevel)).getOrElse(topLevelInitialState)
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

  private val initialization = async sequence {
    Seq(
      // History callback
      jsAccess.registerCallbackAndFlush[String] { pathString =>
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
      } flatMap { historyCallback =>
        client.callAndFlush[Unit]("RegisterHistoryHandler", historyCallback)
      },
      // Event callback
      jsAccess.registerCallbackAndFlush[String] { targetAndType =>
        val Array(renderNum, target, tpe) = targetAndType.split(':')
        if (currentRenderNum.get == renderNum.toInt) {
          calculateEventPropagation(Id(target), tpe) forall { eventId =>
            topLevelComponentInstance.applyEvent(eventId)
          }
          ()
        }
      } flatMap { eventCallback =>
        client.callAndFlush[Unit]("RegisterGlobalEventHandler", eventCallback)
      },
      // FormData progress callback
      jsAccess.registerCallbackAndFlush[String] { descriptorLoadedTotal =>
        val Array(descriptor, loaded, total) = descriptorLoadedTotal.split(':')
        topLevelComponentInstance.handleFormDataProgress(descriptor, loaded.toInt, total.toInt)
      } flatMap { callback =>
        client.callAndFlush[Unit]("RegisterFormDataProgressHandler", callback)
      },
      client.callAndFlush[Unit]("SetRenderNum", 0),
      if (fromScratch) client.callAndFlush("CleanRoot")
      else async.unit,
      if (devMode.isActive) client.callAndFlush("ReloadCss")
      else async.unit
    )
  }

  private def onState(giveStateReader: Boolean) = renderContext.synchronized {
    // Set page url if router exists
    router.fromState
      .lift(topLevelComponentInstance.getState)
      .foreach(path => client.call("ChangePageUrl", path.toString))

    // Prepare render context
    renderContext.swap()

    // Reset all event handlers delays and elements
    topLevelComponentInstance.prepare()

    // Perform rendering
    topLevelComponentInstance.applyRenderContext(
      parameters = (), // Boxed unit as parameter. Top level component doesn't need parameters
      stateReaderOpt =
        if (giveStateReader) stateReaderOpt
        else None,
      rc = renderContext
    )

    // Infer changes
    renderContext.diff(changesPerformer)
    if (devMode.isActive) devMode.saveRenderContext(renderContext)

    // Make korolev ready to next render
    topLevelComponentInstance.dropObsoleteMisc()
    client.call("SetRenderNum", currentRenderNum.incrementAndGet()).runIgnoreResult()
    jsAccess.flush()
  }

  async.run(initialization) {
    case Success(_) =>
      logger.trace("Korolev initialization complete")

      // Perform initial rendering
      if (fromScratch) {
        renderContext.openNode(levsha.XmlNs.html, "body")
        renderContext.closeNode("body")
        renderContext.diff(DiffRenderContext.DummyChangesPerformer)
      } else {
        if (devMode.hasSavedRenderContext) {
          renderContext.swap()
          topLevelComponentInstance.applyRenderContext((), renderContext, stateReaderOpt)
          renderContext.diff(changesPerformer)
          devMode.saveRenderContext(renderContext)
        } else {
          topLevelComponentInstance.applyRenderContext((), renderContext, stateReaderOpt)
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
