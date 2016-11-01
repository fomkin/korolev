package korolev

import java.util.concurrent.atomic.AtomicInteger

import bridge.JSAccess

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

trait Korolev

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
object Korolev extends EventPropagation {

  import VDom._
  import Change._
  import Event._

  type Render[State] = PartialFunction[State, VDom.Node]
  type InitRender[State] = KorolevAccess[State] => Render[State]
  type EventFactory[Payload] = Payload => Event

  def apply[State](jsAccess: JSAccess,
                           initialState: State,
                           initRender: InitRender[State],
                           fromScratch: Boolean)(
      implicit ec: ExecutionContext): Dux[State] = {

    @volatile var propertyAccessors = Map.empty[ElementAccessor, String]
    @volatile var events = Map.empty[String, Event]
    val currentRenderNum = new AtomicInteger(0)

    // Prepare frontend
    jsAccess.global.getAndSaveAs("Korolev", "@Korolev")
    val korolevJS = jsAccess.obj("@Korolev")
    val localDux = Dux[State](initialState)

    def updateMisc(renderResult: VDom.Node) = {
      val misc = collectMisc(Id(0), renderResult)
      events = misc.collect { case (id, event: Event) => s"$id:${event.`type`}:${event.phase}" -> event }.toMap
      propertyAccessors = misc.collect { case (id, pa: ElementAccessor) => pa -> id.toString }.toMap
    }

    jsAccess.registerCallback[String] { targetAndType =>
      val Array(renderNum, target, tpe) = targetAndType.split(':')
      if (currentRenderNum.get == renderNum.toInt)
        propagateEvent(events, Id(target), tpe)
    } foreach { eventCallback =>
      korolevJS.call("RegisterGlobalEventHandler", eventCallback)
      korolevJS.call("SetRenderNum", 0)

      val korolevAccess = new KorolevAccess[State] {

        def event[P](eventType: String, eventPhase: Phase)(
          onFire: P => EventResult[State])(payload: P): Event = {

          case class EventImpl(`type`: String,
            phase: Event.Phase,
            payload: P,
            onFire: P => EventResult[State]) extends Event {

            def fire(): Boolean = {
              val result = onFire(payload)
              result._immediateTransition.foreach(dux.apply)
              result._deferredTransition foreach { actionFuture =>
                actionFuture onComplete {
                  case Success(action) => dux.apply(action)
                  case Failure(exception) => exception.printStackTrace()
                }
              }
              !result._stopPropagation
            }

            override def equals(obj: Any): Boolean = obj match {
              case event: EventImpl => event.onFire.getClass == onFire.getClass && super.equals(obj)
              case _ => super.equals(obj)
            }
          }

          EventImpl(eventType, eventPhase, payload, onFire)
        }

        def id(): ElementAccessor = {
          new ElementAccessor {
            def get[T](property: Symbol): Future[T] = propertyAccessors.get(this) match {
              case Some(id) =>
                val future =
                  korolevJS.call("ExtractProperty", id, property.name)
                jsAccess.flush()
                future
              case None =>
                Future.failed(
                  new Exception("No element matched for accessor"))
            }
          }
        }

        def dux = localDux
      }

      val render = initRender(korolevAccess).lift
      @volatile var lastRender =
        if (fromScratch) VDom.Node("body", Nil, Nil, Nil)
        else render(initialState).get
      updateMisc(lastRender)

      val onState: State => Unit = { state =>
        val startRenderTime = System.nanoTime()
        korolevJS.call("SetRenderNum", currentRenderNum.incrementAndGet())
        render(state) match {
          case Some(newRender) =>
            val changes = VDom.changes(lastRender, newRender)
            updateMisc(newRender)
            lastRender = newRender

            changes foreach {
              case Create(id, childId, tag) =>
                korolevJS.call("Create", id.toString, childId.toString, tag)
              case CreateText(id, childId, text) =>
                korolevJS.call("CreateText", id.toString, childId.toString, text)
              case Remove(id, childId) =>
                korolevJS.call("Remove", id.toString, childId.toString)
              case SetAttr(id, name, value, isProperty) =>
                korolevJS.call("SetAttr", id.toString, name, value, isProperty)
              case RemoveAttr(id, name, isProperty) =>
                korolevJS.call("RemoveAttr", id.toString, name, isProperty)
              case _ =>
            }
            jsAccess.flush()
          case None =>
            println(s"Render is nod defined for ${state.getClass.getSimpleName}")
        }
        val t = (System.nanoTime() - startRenderTime) / 1000000000d
        println(s"Render time: $t")
      }

      localDux.subscribe(onState)
      if (fromScratch) onState(initialState)
      else jsAccess.flush()
    }

    if (fromScratch)
      korolevJS.call("CleanRoot")

    jsAccess.flush()
    localDux
  }
}
