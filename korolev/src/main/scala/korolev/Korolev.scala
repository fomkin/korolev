package korolev

import java.util.concurrent.atomic.AtomicInteger

import bridge.JSAccess
import korolev.ApplicationContext._
import korolev.Async.AsyncOps
import korolev.Component.{ComponentInstance, EventRegistry, Frontend}
import levsha.events.calculateEventPropagation
import levsha.impl.DiffRenderContext
import levsha.impl.DiffRenderContext.ChangesPerformer
import levsha.{Document, Id, XmlNs}
import slogging.LazyLogging

import scala.util.{Failure, Success, Try}
import korolev.util.Scheduler

abstract class Korolev[F[+ _]: Async, S, M] {
  def jsAccess: JSAccess[F]
  def resolveFormData(descriptor: String, formData: Try[FormData]): Unit
  def topLevelComponentInstance: ComponentInstance[F, S, M, S, M]
}

object Korolev {

  def apply[F[+ _]: Async, S, M](identifier: String,
                                 ja: JSAccess[F],
                                 initialState: S,
                                 render: PartialFunction[S, Document.Node[ApplicationContext.Effect[F, S, M]]],
                                 router: Router[F, S, S],
                                 fromScratch: Boolean)(implicit scheduler: Scheduler[F]): Korolev[F, S, M] =
    new Korolev[F, S, M] with LazyLogging {

      val async = Async[F]
      //implicit val er = Async.ErrorReporter(e => logger.error(e.getMessage, e))

      def handleAsyncError(message: Throwable => String): (Try[_] => Unit) = {
        case Failure(e) => logger.error(message(e), e)
        case Success(_) => // do nothing
      }

      val jsAccess = ja
      val client = {
        // Prepare frontend
        jsAccess.global.getAndSaveAs("Korolev", "@Korolev")
        jsAccess.obj("@Korolev")
      }
      // TODO Dev Mode
      //val devMode = new RenderContextDevMode(identifier, fromScratch)
      val topLevelComponentInstance = {
        val renderer = render.lift
        val frontend = new Frontend[F] {
          def listenEvent(name: String, preventDefault: Boolean): F[Unit] =
            client.callAndFlush("ListenEvent", name, preventDefault)
          def uploadForm(id: Id, descriptor: String): F[Unit] =
            client.callAndFlush("UploadForm", id.mkString, descriptor)
          def focus(id: Id): F[Unit] =
            client.callAndFlush("Focus", id.mkString)
          def setAttr[T](id: Id, xmlNs: String, name: String, value: T, isProperty: Boolean): F[Unit] =
            client.callAndFlush("SetAttr", id.mkString, xmlNs, name, value, isProperty)
          def extractProperty[T](id: Id, name: String): F[T] =
            client.callAndFlush("ExtractProperty", id.mkString, name)
        }
        val eventRegistry = new EventRegistry[F](frontend)
        val component = new Component[F, S, M](Component.TopLevelComponentId) {
          def render(state: S): Document.Node[Effect[F, S, M]] = {
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
        new ComponentInstance[F, S, M, S, M](initialState, frontend, eventRegistry, component)
      }
      val renderContext = DiffRenderContext[Effect[F, S, M]]()
      // TODO Dev Mode: restore render context state
//      (
//        onMisc = effectsReactor.miscCallback,
//        savedBuffer = devMode.loadRenderContext()
//      )

      val changesPerformer = new ChangesPerformer {
        private def isProp(name: String) = name.charAt(0) == '^'
        private def escapeName(name: String, isProp: Boolean) =
          if (isProp) name.substring(1) else name

        def remove(id: Id): Unit =
          client.call("Remove", id.parent.get.mkString, id.mkString).runIgnoreResult()
        def createText(id: Id, text: String): Unit =
          client.call("CreateText", id.parent.get.mkString, id.mkString, text).runIgnoreResult()
        def create(id: Id, xmlNs: String, tag: String): Unit = {
          val parent = id.parent.fold("0")(_.mkString)
          val pXmlns = if (xmlNs eq levsha.XmlNs.html.uri) 0 else xmlNs
          client.call("Create", parent, id.mkString, pXmlns, tag).runIgnoreResult()
        }
        def setAttr(id: Id, xmlNs: String, name: String, value: String): Unit = {
          val p = isProp(name)
          val n = escapeName(name, p)
          val pXmlns = if (xmlNs eq levsha.XmlNs.html.uri) 0 else xmlNs
          client.call("SetAttr", id.mkString, pXmlns, n, value, p).runIgnoreResult()
        }
        def removeAttr(id: Id, xmlNs: String, name: String): Unit = {
          val p = isProp(name)
          val n = escapeName(name, p)
          val pXmlns = if (xmlNs eq levsha.XmlNs.html.uri) 0 else xmlNs
          client.call("RemoveAttr", id.mkString, pXmlns, n, p).runIgnoreResult()
        }
      }

      def resolveFormData(descriptor: String, formData: Try[FormData]): Unit = {
        topLevelComponentInstance.resolveFormData(descriptor, formData)
      }

      val currentRenderNum = new AtomicInteger(0)

      val initialization = async sequence {
        Seq(
          // History callback
          jsAccess.registerCallbackAndFlush[String] { pathString =>
            val path = Router.Path.fromString(pathString)
            val maybeState = router.toState.lift((topLevelComponentInstance.getState, path))
            maybeState foreach { asyncState =>
              asyncState run {
                case Success(newState) =>
                  topLevelComponentInstance.setState(newState, force = true)
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
          if (fromScratch) client.callAndFlush("CleanRoot") else async.unit //,
          //if (devMode.isActive) client.callAndFlush("ReloadCss") else async.unit
        )
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
//            if (devMode.hasSavedRenderContext) {
//              renderContext.swap()
//              renderInitialState()
//              renderContext.diff(changesPerformer)
//              devMode.saveRenderContext(renderContext)
//            } else {
              topLevelComponentInstance.applyRenderContext(renderContext)
              renderContext.diff(DiffRenderContext.DummyChangesPerformer)
//              if (devMode.isActive) devMode.saveRenderContext(renderContext)
//            }
          }

          val onState = () => {
            // Set page url if router exists
            router.fromState
              .lift(topLevelComponentInstance.getState)
              .foreach(path => client.call("ChangePageUrl", path.toString))

            // Prepare render context
            renderContext.swap()
            // Reset all event handlers delays and elements
            topLevelComponentInstance.prepare()
            // Perform rendering
            topLevelComponentInstance.applyRenderContext(renderContext)
            // Infer changes
            renderContext.diff(changesPerformer)
//              if (devMode.isActive)
//                devMode.saveRenderContext(renderContext)
            topLevelComponentInstance.dropObsoleteMisc()
            client.call("SetRenderNum", currentRenderNum.incrementAndGet()).runIgnoreResult()
            jsAccess.flush()
          }

          topLevelComponentInstance.subscribeStateChange(onState)
          if (fromScratch) onState()
          else jsAccess.flush()
        case Failure(e) =>
          logger.error("Error occurred on event callback registration", e)
      }
    }

//  class RenderContextDevMode(identifier: String, fromScratch: Boolean) {
//
//    lazy val file = new File(DevMode.renderStateDirectory, identifier)
//
//    lazy val hasSavedRenderContext = DevMode.isActive && file.exists && !fromScratch
//
//    def isActive = DevMode.isActive
//
//    def loadRenderContext() = if (hasSavedRenderContext) {
//      val nioFile = new RandomAccessFile(file, "r")
//      val channel = nioFile.getChannel
//      try {
//        val buffer = ByteBuffer.allocate(channel.size.toInt)
//        channel.read(buffer)
//        buffer.position(0)
//        Some(buffer)
//      } finally {
//        nioFile.close()
//        channel.close()
//      }
//    } else {
//      None
//    }
//
//    def saveRenderContext(renderContext: DiffRenderContext[_]) = {
//      val nioFile = new RandomAccessFile(file, "rw")
//      val channel = nioFile.getChannel
//      try {
//        val buffer = renderContext.save()
//        channel.write(buffer)
//      } finally {
//        nioFile.close()
//        channel.close()
//      }
//    }
//  }
}
