package korolev

import java.util.concurrent.atomic.AtomicInteger

import bridge.JSAccess
import korolev.BrowserEffects.{BrowserAccess, ElementId}

import scala.language.higherKinds
import scala.util.{Failure, Success}

trait Korolev

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
object Korolev extends EventPropagation {

  import VDom._
  import Change._

  type Render[S] = PartialFunction[S, VDom.Node]

  def apply[F[_]: Async, S](jsAccess: JSAccess[F],
                           initialState: S,
                           render: Render[S],
                           fromScratch: Boolean): Dux[F, S] = {

    @volatile var elementIds = Map.empty[BrowserEffects.ElementId, String]
    @volatile var events = Map.empty[String, BrowserEffects.Event[F, S]]

    val currentRenderNum = new AtomicInteger(0)

    // Prepare frontend
    jsAccess.global.getAndSaveAs("Korolev", "@Korolev")
    val korolevJS = jsAccess.obj("@Korolev")
    val localDux = Dux[F, S](initialState)

    def updateMisc(renderResult: VDom.Node) = {

      val misc = collectMisc(Id(0), renderResult)

      events = {
        val xs = misc.collect {
          case (id, event: BrowserEffects.Event[_, _]) =>
            val typedEvent = event.asInstanceOf[BrowserEffects.Event[F, S]]
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
      def property[T](eId: ElementId, propName: Symbol): F[T] = elementIds.get(eId) match {
        case Some(id) =>
          val future = korolevJS.call[T]("ExtractProperty", id, propName.name)
          jsAccess.flush()
          future
        case None =>
          Async[F].fromTry(Failure(new Exception("No element matched for accessor")))
      }
    }

    val eventCallbackF = jsAccess.registerCallback[String] { targetAndType =>
      val Array(renderNum, target, tpe) = targetAndType.split(':')
      if (currentRenderNum.get == renderNum.toInt)
        propagateEvent(events, localDux.apply, browserAccess, Id(target), tpe)
    }
    Async[F].run(eventCallbackF) {
      case Success(eventCallback) =>
        korolevJS.call("RegisterGlobalEventHandler", eventCallback)
        korolevJS.call("SetRenderNum", 0)

        val renderOpt = render.lift

        @volatile var lastRender =
          if (fromScratch) VDom.Node("body", Nil, Nil, Nil)
          else renderOpt(initialState).get

        updateMisc(lastRender)

        val onState: S => Unit = { state =>
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
      case Failure(e) =>
        e.printStackTrace()
    }

    if (fromScratch)
      korolevJS.call("CleanRoot")

    jsAccess.flush()
    localDux
  }
}
