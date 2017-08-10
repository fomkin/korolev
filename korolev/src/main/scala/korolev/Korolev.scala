package korolev

import java.io.{File, RandomAccessFile}
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

import bridge.JSAccess
import korolev.ApplicationContext._
import korolev.Async.{AsyncOps, Promise}
import korolev.StateManager.Transition
import levsha.events.{EventId, calculateEventPropagation}
import levsha.impl.DiffRenderContext
import levsha.impl.DiffRenderContext.ChangesPerformer
import levsha.{Document, Id}
import slogging.LazyLogging

import scala.collection.mutable
import scala.util.{Failure, Random, Success, Try}

abstract class Korolev[F[+ _]: Async, S, M] {
  def stateManager: StateManager[F, S]
  def jsAccess: JSAccess[F]
  def resolveFormData(descriptor: String, formData: Try[FormData]): Unit
}

object Korolev {

  trait MutableMapFactory {
    def apply[K, V]: mutable.Map[K, V]
  }

  object defaultMutableMapFactory extends MutableMapFactory {
    def apply[K, V]: mutable.Map[K, V] =
      mutable.Map.empty
  }

  def apply[F[+ _]: Async, S, M](identifier: String,
                                 sm: StateManager[F, S],
                                 ja: JSAccess[F],
                                 initialState: S,
                                 render: PartialFunction[S, Document.Node[ApplicationContext.Effect[F, S, M]]],
                                 router: Router[F, S, S],
                                 messageHandler: PartialFunction[M, Unit],
                                 fromScratch: Boolean,
                                 createMutableMap: MutableMapFactory = defaultMutableMapFactory): Korolev[F, S, M] =
    new Korolev[F, S, M] with LazyLogging {

      val async = Async[F]
      //implicit val er = Async.ErrorReporter(e => logger.error(e.getMessage, e))

      def handleAsyncError(message: Throwable => String): (Try[_] => Unit) = {
        case Failure(e) => logger.error(message(e), e)
        case Success(_) => // do nothing
      }

      val jsAccess = ja
      val stateManager = sm
      val client = {
        // Prepare frontend
        jsAccess.global.getAndSaveAs("Korolev", "@Korolev")
        jsAccess.obj("@Korolev")
      }
      val effectsReactor = new EffectsReactor[F, S, M](
        onStartDelay = delay => delay.start(stateManager),
        onCancelDelay = delay => delay.cancel(),
        onNewEventType = { `type` =>
          val rpc = client.call("ListenEvent", `type`.name, false)
          async.run(rpc)(handleAsyncError(_ => "Error occurred when invoking ListenEvent"))
        }
      )
      val devMode = new RenderContextDevMode(identifier, fromScratch)
      val renderContext = DiffRenderContext[Effect[F, S, M]](
        onMisc = effectsReactor.miscCallback,
        savedBuffer = devMode.loadRenderContext()
      )

      val renderer = render.lift
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
        formDataPromises.get(descriptor) foreach { promise =>
          promise.complete(formData)
        }
        // Remove promise and onProgress handler
        // when formData loading is complete
        formDataProgressTransitions.remove(descriptor)
        formDataPromises.remove(descriptor)
        ()
      }

      val currentRenderNum = new AtomicInteger(0)
      val formDataPromises = createMutableMap[String, Promise[F, FormData]]
      val formDataProgressTransitions = createMutableMap[String, (Int, Int) => Transition[S]]

      val browserAccess = new Access[F, S, M] {

        private def noElementException[T]: F[T] = {
          val exception = new Exception("No element matched for accessor")
          async.fromTry(Failure(exception))
        }

        def property[T](elementId: ElementId[F, S, M]): PropertyHandler[F, T] = {
          val idF = getId(elementId)
          new PropertyHandler[F, T] {
            def get(propName: Symbol): F[T] = idF flatMap { id =>
              client.callAndFlush("ExtractProperty", id.mkString, propName.name)
            }
            def set(propName: Symbol, value: T): F[Unit] = idF flatMap { id =>
              // Xmlns argument is empty cause it will be ignored
              client.callAndFlush("SetAttr", id.mkString, "", propName.name, value, true)
            }
          }
        }

        def property[T](id: ElementId[F, S, M], propName: Symbol): F[T] =
          property[T](id).get(propName)

        def focus(elementId: ElementId[F, S, M]): F[Unit] = {
          val idF = getId(elementId)
          idF flatMap { id =>
            client.callAndFlush[Unit]("Focus", id.mkString)
          }
        }

        private def getId(elementId: ElementId[F, S, M]): F[Id] =
          effectsReactor
            .elements
            .get(elementId)
            .fold(noElementException[Id])(id => async.pure(id))

        def downloadFormData(elementId: ElementId[F, S, M]): FormDataDownloader[F, S] = new FormDataDownloader[F, S] {
          val descriptor = Random.alphanumeric.take(5).mkString

          def start(): F[FormData] = effectsReactor.elements.get(elementId) match {
            case Some(id) =>
              val promise = async.promise[FormData]
              val future = client.call[Unit]("UploadForm", id.mkString, descriptor)
              formDataPromises.put(descriptor, promise)
              jsAccess.flush()
              future.flatMap(_ => promise.future)
            case None =>
              async.fromTry(Failure(new Exception("No element matched for accessor")))
          }
          def onProgress(f: (Int, Int) => Transition[S]): this.type = {
            formDataProgressTransitions.put(descriptor, f)
            this
          }
        }

        def publish(message: M): F[Unit] = {
          async.pure(messageHandler(message))
        }
      }

      val initialization = async sequence {
        Seq(
          // History callback
          jsAccess.registerCallbackAndFlush[String] { pathString =>
            val path = Router.Path.fromString(pathString)
            val maybeState = router.toState.lift((stateManager.state, path))
            maybeState foreach { asyncState =>
              val unit = async.flatMap(asyncState)(stateManager.update)
              async.run(unit) {
                case Success(_) => // do nothing
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
              calculateEventPropagation(Id(target), tpe) foreach { eventId =>
                val eventResultOpt = effectsReactor.events.get(eventId) map {
                  case event: ApplicationContext.EventWithAccess[F, S, M] => event.effect(browserAccess)
                  case event: ApplicationContext.SimpleEvent[F, S, M] => event.effect()
                }
                eventResultOpt.fold(true) { er: EventResult[F, S] =>
                  // Apply immediate transition
                  er.it.fold(async.unit)(it => stateManager(it)) flatMap { _ =>
                    // Apply deferred transition
                    er.dt.fold(async.unit) { transitionF =>
                      transitionF.flatMap(stateManager.apply)
                    }
                  } run {
                    case Success(_) => // ok transitions was applied
                    case Failure(e) => logger.error("Exception during applying transition", e)
                  }
                  !er.sp
                }
              }
            }
              //propagateEvent(events(), stateManager, browserAccess, Id(target), tpe)
          } flatMap { eventCallback =>
            client.callAndFlush[Unit]("RegisterGlobalEventHandler", eventCallback)
          },
          // FormData progress callback
          jsAccess.registerCallbackAndFlush[String] { descriptorLoadedTotal =>
            val Array(descriptor, loaded, total) = descriptorLoadedTotal.split(':')
            formDataProgressTransitions.get(descriptor) foreach { f =>
              stateManager(f(loaded.toInt, total.toInt))
            }
          } flatMap { callback =>
            client.callAndFlush[Unit]("RegisterFormDataProgressHandler", callback)
          },
          client.callAndFlush[Unit]("SetRenderNum", 0),
          if (fromScratch) client.callAndFlush("CleanRoot") else async.unit,
          if (devMode.isActive) client.callAndFlush("ReloadCss") else async.unit
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
            def renderInitialState() = {
              renderer(initialState) match {
                case Some(node) => node(renderContext)
                case None => logger.error("Rendering function is not defined for initial state")
              }
            }
            if (devMode.hasSavedRenderContext) {
              renderContext.swap()
              renderInitialState()
              renderContext.diff(changesPerformer)
              devMode.saveRenderContext(renderContext)
            } else {
              renderInitialState()
              renderContext.diff(DiffRenderContext.DummyChangesPerformer)
              if (devMode.isActive) devMode.saveRenderContext(renderContext)
            }
          }

          val onState: (S => Unit) = { state =>
            // Set page url if router exists
            router.fromState
              .lift(state)
              .foreach(path => client.call("ChangePageUrl", path.toString))
            // Perform rendering
            renderer(state) match {
              case Some(node) =>
                // Perform changes only when renderer for state is defined
                renderContext.swap()
                // Reset all event handlers delays and elements
                effectsReactor.prepare()
                node(renderContext)
                renderContext.diff(changesPerformer)
                if (devMode.isActive)
                  devMode.saveRenderContext(renderContext)
                effectsReactor.cancelObsoleteDelays()
              case None =>
                logger.warn(s"Render is not defined for ${state.getClass.getSimpleName}")
            }
            client.call("SetRenderNum", currentRenderNum.incrementAndGet()).runIgnoreResult()
            jsAccess.flush()
          }

          stateManager.subscribe(onState)
          if (fromScratch) onState(initialState)
          else jsAccess.flush()
        case Failure(e) =>
          logger.error("Error occurred on event callback registration", e)
      }
    }

  private class EffectsReactor[F[+ _]: Async, S, M](
    onNewEventType: Symbol => Unit,
    onStartDelay: ApplicationContext.Delay[F, S, M] => Unit,
    onCancelDelay: ApplicationContext.Delay[F, S, M] => Unit) {

    val knownEventTypes = mutable.Set('submit)
    val markedDelays    = mutable.Set.empty[Id] // Set of the delays which are should survive
    val elements        = mutable.Map.empty[ApplicationContext.ElementId[F, S, M], Id]
    val events          = mutable.Map.empty[EventId, ApplicationContext.Event[F, S, M]]
    val delays          = mutable.Map.empty[Id, ApplicationContext.Delay[F, S, M]]

    def miscCallback(id: Id, effect: Effect[F, S, M]): Unit = this.synchronized {
      effect match {
        case event: ApplicationContext.Event[F, S, M] =>
          events.put(EventId(id, event.`type`.name, event.phase), event)
          if (!knownEventTypes.contains(event.`type`)) {
            knownEventTypes += event.`type`
            onNewEventType(event.`type`)
          }
        case delay: ApplicationContext.Delay[F, S, M] =>
          markedDelays += id
          if (!delays.contains(id)) {
            delays.put(id, delay)
            onStartDelay(delay)
          }
        case element: ApplicationContext.ElementId[F, S, M] =>
          elements.put(element, id)
      }
      ()
    }

    /** Should be invoked before rendering */
    def prepare(): Unit = {
      markedDelays.clear()
      elements.clear()
      events.clear()
      // Remove only finished delays
      delays foreach {
        case (id, delay) =>
          if (delay.finished)
            delays.remove(id)
      }
    }

    /** Remove all delays which was not marked during rendering */
    def cancelObsoleteDelays(): Unit = {
      delays foreach {
        case (id, delay) =>
          if (!markedDelays.contains(id)) {
            delays.remove(id)
            onCancelDelay(delay)
          }
      }
    }
  }

  class RenderContextDevMode(identifier: String, fromScratch: Boolean) {

    lazy val file = new File(DevMode.renderStateDirectory, identifier)

    lazy val hasSavedRenderContext = DevMode.isActive && file.exists && !fromScratch

    def isActive = DevMode.isActive

    def loadRenderContext() = if (hasSavedRenderContext) {
      val nioFile = new RandomAccessFile(file, "r")
      val channel = nioFile.getChannel
      try {
        val buffer = ByteBuffer.allocate(channel.size.toInt)
        channel.read(buffer)
        buffer.position(0)
        Some(buffer)
      } finally {
        nioFile.close()
        channel.close()
      }
    } else {
      None
    }

    def saveRenderContext(renderContext: DiffRenderContext[_]) = {
      val nioFile = new RandomAccessFile(file, "rw")
      val channel = nioFile.getChannel
      try {
        val buffer = renderContext.save()
        channel.write(buffer)
      } finally {
        nioFile.close()
        channel.close()
      }
    }
  }
}
