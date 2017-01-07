package korolev

import java.util.concurrent.atomic.AtomicInteger

import bridge.JSAccess
import korolev.Effects.{Access, ElementId}
import korolev.Async.AsyncOps
import slogging.LazyLogging

import scala.language.higherKinds
import scala.util.{Failure, Success}

trait Korolev

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
object Korolev extends EventPropagation with LazyLogging {

  import VDom._
  import Change._

  def apply[F[+_]: Async, S, M](
                           localDux: Dux[F, S],
                           jsAccess: JSAccess[F],
                           initialState: S,
                           render: Render[S],
                           router: Router[F, S, S],
                           messageHandler: PartialFunction[M, Unit],
                           fromScratch: Boolean): Dux[F, S] = {

    // This event type should be listen on client side
    @volatile var enabledEvents = Set('submit)
    @volatile var elementIds = Map.empty[Effects.ElementId, String]
    @volatile var events = Map.empty[String, Effects.Event[F, S, M]]

    val currentRenderNum = new AtomicInteger(0)

    // Prepare frontend
    jsAccess.global.getAndSaveAs("Korolev", "@Korolev")
    val client = jsAccess.obj("@Korolev")

    def updateMisc(renderResult: VDom.Node) = {

      val misc = collectMisc(Id(0), renderResult)

      events = {
        val xs = misc.collect {
          case (id, event: Effects.Event[_, _, _]) =>
            val typedEvent = event.asInstanceOf[Effects.Event[F, S, M]]
            s"$id:${event.`type`.name}:${event.phase}" -> typedEvent
        }
        xs.toMap
      }

      events.values foreach { event =>
        val `type` = event.`type`
        if (!enabledEvents.contains(`type`)) {
          enabledEvents = enabledEvents + `type`
          Async[F].run(client.call("ListenEvent", `type`.name, false)) {
            case Success(_) => // do nothing
            case Failure(e) => logger.error("Error occurred when invoking ListenEvent", e)
          }
        }
      }

      elementIds = {
        val xs = misc.collect {
          case (id, eId: Effects.ElementId) => eId -> id.toString
        }
        xs.toMap
      }
    }

    val browserAccess = new Access[F, M] {
      def property[T](eId: ElementId, propName: Symbol): F[T] = elementIds.get(eId) match {
        case Some(id) =>
          val future = client.call[T]("ExtractProperty", id, propName.name)
          jsAccess.flush()
          future
        case None =>
          Async[F].fromTry(Failure(new Exception("No element matched for accessor")))
      }

      def publish(message: M): F[Unit] = {
        Async[F].pure(messageHandler(message))
      }
    }

    val initialization = Async[F] sequence {
      Seq(
        jsAccess.registerCallbackAndFlush[String] { pathString =>
          val path = Router.Path.fromString(pathString)
          val maybeState = router.toState.lift(localDux.state, path)
          maybeState foreach { asyncState =>
            val unit = Async[F].flatMap(asyncState)(localDux.update)
            Async[F].run(unit) {
              case Success(_) => // do nothing
              case Failure(e) => logger.error("Error occurred when updating state", e)
            }
          }
        } flatMap { historyCallback =>
          client.callAndFlush[Unit]("RegisterHistoryHandler", historyCallback)
        },
        jsAccess.registerCallbackAndFlush[String] { targetAndType =>
          val Array(renderNum, target, tpe) = targetAndType.split(':')
          if (currentRenderNum.get == renderNum.toInt)
            propagateEvent(events, localDux.apply, browserAccess, Id(target), tpe)
        } flatMap { eventCallback =>
          client.callAndFlush[Unit]("RegisterGlobalEventHandler", eventCallback)
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
          router.
            fromState.lift(state).
            foreach(path => client.call("ChangePageUrl", path.toString))

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
              logger.warn(s"Render is nod defined for ${state.getClass.getSimpleName}")
          }
        }

        localDux.subscribe(onState)
        if (fromScratch) onState(initialState)
        else jsAccess.flush()
      case Failure(e) => logger.error("Error occurred on event callback registration", e)
    }

    localDux
  }
}
