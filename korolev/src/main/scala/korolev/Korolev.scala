package korolev

import java.util.concurrent.atomic.AtomicInteger

import bridge.JSAccess
import korolev.BrowserEffects.{BrowserAccess, ElementId}

import scala.concurrent.{ExecutionContext, Future}

trait Korolev

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
object Korolev extends EventPropagation {

  import VDom._
  import Change._

  type Render[State] = PartialFunction[State, VDom.Node]

  def apply[State](jsAccess: JSAccess,
                           initialState: State,
                           render: Render[State],
                           fromScratch: Boolean)(
      implicit ec: ExecutionContext): Dux[State] = {

    @volatile var elementIds = Map.empty[BrowserEffects.ElementId, String]
    @volatile var events = Map.empty[String, BrowserEffects.Event[State]]

    val currentRenderNum = new AtomicInteger(0)

    // Prepare frontend
    jsAccess.global.getAndSaveAs("Korolev", "@Korolev")
    val korolevJS = jsAccess.obj("@Korolev")
    val localDux = Dux[State](initialState)

    def updateMisc(renderResult: VDom.Node) = {

      val misc = collectMisc(Id(0), renderResult)

      events = {
        val xs = misc.collect {
          case (id, event: BrowserEffects.Event[_]) =>
            val typedEvent = event.asInstanceOf[BrowserEffects.Event[State]]
            s"$id:${event.name.name}:${event.phase}" -> typedEvent
        }
        xs.toMap
      }

      elementIds = {
        val xs = misc.collect {
          case (id, eId: BrowserEffects.ElementId) => eId -> id.toString
        }
        xs.toMap
      }
    }

    val browserAccess = new BrowserAccess {
      def property[T](eId: ElementId, propName: Symbol): Future[T] = elementIds.get(eId) match {
        case Some(id) =>
          val future = korolevJS.call("ExtractProperty", id, propName.name)
          jsAccess.flush()
          future
        case None =>
          Future.failed(new Exception("No element matched for accessor"))
      }
    }

    jsAccess.registerCallback[String] { targetAndType =>
      val Array(renderNum, target, tpe) = targetAndType.split(':')
      if (currentRenderNum.get == renderNum.toInt)
        propagateEvent(events, localDux.apply, browserAccess, Id(target), tpe)
    } foreach { eventCallback =>
      korolevJS.call("RegisterGlobalEventHandler", eventCallback)
      korolevJS.call("SetRenderNum", 0)

      val renderOpt = render.lift

      @volatile var lastRender =
        if (fromScratch) VDom.Node("body", Nil, Nil, Nil)
        else renderOpt(initialState).get

      updateMisc(lastRender)

      val onState: State => Unit = { state =>
        val startRenderTime = System.nanoTime()
        korolevJS.call("SetRenderNum", currentRenderNum.incrementAndGet())
        renderOpt(state) match {
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
