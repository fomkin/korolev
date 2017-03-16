package korolev

import java.util.concurrent.atomic.AtomicInteger

import bridge.JSAccess
import korolev.Effects.{Access, ElementId, FormDataDownloader}
import korolev.Async.{AsyncOps, Promise}
import korolev.StateManager.Transition
import korolev.util.AtomicReference
import slogging.LazyLogging

import scala.collection.mutable
import scala.language.{higherKinds, postfixOps}
import scala.util.{Failure, Random, Success, Try}

abstract class Korolev[F[+ _]: Async, S, M] {
  def stateManager: StateManager[F, S]
  def jsAccess: JSAccess[F]
  def resolveFormData(descriptor: String, formData: Try[FormData]): Unit
}

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
object Korolev {

  import VDom._
  import Change._

  trait MutableMapFactory {
    def apply[K, V]: mutable.Map[K, V]
  }

  object defaultMutableMapFactory extends MutableMapFactory {
    def apply[K, V]: mutable.Map[K, V] =
      mutable.Map.empty
  }

  def apply[F[+ _]: Async, S, M](sm: StateManager[F, S],
                                 ja: JSAccess[F],
                                 initialState: S,
                                 render: Render[S],
                                 router: Router[F, S, S],
                                 messageHandler: PartialFunction[M, Unit],
                                 fromScratch: Boolean,
                                 createMutableMap: MutableMapFactory = defaultMutableMapFactory): Korolev[F, S, M] =
    new Korolev[F, S, M] with EventPropagation with LazyLogging {

      // Public properties
      val stateManager = sm
      val jsAccess = ja

      def resolveFormData(descriptor: String, formData: Try[FormData]): Unit = {
        formDataPromises.get(descriptor) foreach { promise =>
          promise.complete(formData)
        }
        // Remove promise and onProgress handler
        // when formData loading is complete
        formDataProgressTransitions.remove(descriptor)
        formDataPromises.remove(descriptor)
      }

      def updateMisc(renderResult: VDom.Node): Unit = {
        val misc = collectMisc(Id(0), renderResult)

        events() = misc.collect {
          case (id, event: Effects.Event[_, _, _]) =>
            val typedEvent = event.asInstanceOf[Effects.Event[F, S, M]]
            s"$id:${event.`type`.name}:${event.phase}" -> typedEvent
        } toMap

        events().values foreach { event =>
          val `type` = event.`type`
          if (!enabledEvents().contains(`type`)) {
            enabledEvents.transform(_ + `type`)
            Async[F].run(client.call("ListenEvent", `type`.name, false)) {
              case Success(_) => // do nothing
              case Failure(e) =>
                logger.error("Error occurred when invoking ListenEvent", e)
            }
          }
        }

        elementIds() = misc.collect {
          case (id, eId: Effects.ElementId) =>
            eId -> id.toString
        } toMap
      }

      // This event type should be listen on client side
      val enabledEvents = AtomicReference(Set('submit))
      val elementIds = AtomicReference(Map.empty[Effects.ElementId, String])
      val events = AtomicReference(Map.empty[String, Effects.Event[F, S, M]])
      val currentRenderNum = new AtomicInteger(0)
      val formDataPromises = createMutableMap[String, Promise[F, FormData]]
      val formDataProgressTransitions = createMutableMap[String, (Int, Int) => Transition[S]]

      val client = {
        // Prepare frontend
        jsAccess.global.getAndSaveAs("Korolev", "@Korolev")
        jsAccess.obj("@Korolev")
      }

      val browserAccess = new Access[F, S, M] {

        def property[T](eId: ElementId, propName: Symbol): F[T] =
          elementIds().get(eId) match {
            case Some(id) =>
              val future = client.call[T]("ExtractProperty", id, propName.name)
              jsAccess.flush()
              future
            case None =>
              Async[F].fromTry(
                Failure(new Exception("No element matched for accessor")))
          }

        def downloadFormData(eId: ElementId): FormDataDownloader[F, S] = new FormDataDownloader[F, S] {

          val descriptor = Random.alphanumeric.take(5).mkString

          def start(): F[FormData] = elementIds().get(eId) match {
            case Some(id) =>
              val promise = Async[F].promise[FormData]
              val future = client.call[Unit]("UploadForm", id, descriptor)
              formDataPromises.put(descriptor, promise)
              jsAccess.flush()
              future.flatMap(_ => promise.future)
            case None =>
              Async[F].fromTry(
                Failure(new Exception("No element matched for accessor")))
          }
          def onProgress(f: (Int, Int) => Transition[S]): this.type = {
            formDataProgressTransitions.put(descriptor, f)
            this
          }
        }

        def publish(message: M): F[Unit] = {
          Async[F].pure(messageHandler(message))
        }
      }

      val initialization = Async[F] sequence {
        Seq(
          // History callback
          jsAccess.registerCallbackAndFlush[String] { pathString =>
            val path = Router.Path.fromString(pathString)
            val maybeState = router.toState.lift(stateManager.state, path)
            maybeState foreach { asyncState =>
              val unit = Async[F].flatMap(asyncState)(stateManager.update)
              Async[F].run(unit) {
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
            if (currentRenderNum.get == renderNum.toInt)
              propagateEvent(events(), stateManager.apply, browserAccess, Id(target), tpe)
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
          if (fromScratch) client.callAndFlush("CleanRoot") else Async[F].unit
        )
      }

      Async[F].run(initialization) {
        case Success(_) =>
          logger.trace("Korolev initialization complete")
          val renderOpt = render.lift

          @volatile var lastRender =
            if (fromScratch) VDom.Node("body", Nil, Nil, Nil)
            else renderOpt(initialState).get

          updateMisc(lastRender)

          val onState: S => Unit = { state =>
            // Set page url if router exists
            router.fromState
              .lift(state)
              .foreach(path => client.call("ChangePageUrl", path.toString))

            client.call("SetRenderNum", currentRenderNum.incrementAndGet())

            renderOpt(state) match {
              case Some(newRender) =>
                val changes = VDom.changes(lastRender, newRender)
                updateMisc(newRender)
                lastRender = newRender

                changes foreach {
                  case Create(id, childId, tag) =>
                    client.call("Create", id.toString, childId.toString, tag)
                  case CreateText(id, childId, text) =>
                    client.call("CreateText", id.toString, childId.toString, text)
                  case Remove(id, childId) =>
                    client.call("Remove", id.toString, childId.toString)
                  case SetAttr(id, name, value, isProperty) =>
                    client.call("SetAttr", id.toString, name, value, isProperty)
                  case RemoveAttr(id, name, isProperty) =>
                    client.call("RemoveAttr", id.toString, name, isProperty)
                  case _ =>
                }
                jsAccess.flush()
              case None =>
                logger.warn(
                  s"Render is nod defined for ${state.getClass.getSimpleName}")
            }
          }

          stateManager.subscribe(onState)
          if (fromScratch) onState(initialState)
          else jsAccess.flush()
        case Failure(e) =>
          logger.error("Error occurred on event callback registration", e)
      }
    }
}
