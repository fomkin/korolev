package korolev

import bridge.JSAccess

import scala.collection.concurrent.TrieMap
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

  trait KorolevAccess[Action] {
    def event[Payload](`type`: String, phase: Phase = AtTarget)(
        onFile: Payload => (Boolean, Future[Action]))(payload: Payload): Event
    def id(pl: Any): PropertyAccessor
  }

  type Render[State] = State => VDom.Node
  type InitRender[State, Action] = KorolevAccess[Action] => Render[State]
  type EventFactory[Payload] = Payload => Event

  def apply[State, Action](jsAccess: JSAccess,
                           initialState: State,
                           reducer: Dux.Reducer[State, Action],
                           initRender: InitRender[State, Action])(
      implicit ec: ExecutionContext): Dux[State, Action] = {

    val propertyAccessors = TrieMap.empty[PropertyAccessor, String]
    val events = TrieMap.empty[String, Event]

    @volatile var lastRender = VDom.Node("void", Nil, Nil, Nil)

    // Prepare frontend
    jsAccess.global.getAndSaveAs("Korolev", "@Korolev")
    val korolevJS = jsAccess.obj("@Korolev")
    val dux = Dux[State, Action](initialState, reducer)

    jsAccess.registerCallback[String] { targetAndType =>
      val Array(target, tpe) = targetAndType.split(':')
      propagateEvent(events, Id(target), tpe)

    } foreach { eventCallback =>
      korolevJS.call("RegisterGlobalEventHandler", eventCallback)

      var reduceRealT = 0l

      val korolevAccess = new KorolevAccess[Action] {
        def event[Payload](eventType: String, eventPhase: Phase)(
            onFire: Payload => (Boolean, Future[Action]))(pl: Payload): Event = {
          new Event {
            val `type` = eventType
            val phase = eventPhase
            def payload = pl
            def fire(): Boolean = {
              val (continuePropagation, future) = onFire(pl)
              future onComplete {
                case Success(action) =>
                  reduceRealT = System.currentTimeMillis()
                  dux.dispatch(action)
                case Failure(exception) => exception.printStackTrace()
              }
              continuePropagation
            }
          }
        }

        def id(pl: Any): PropertyAccessor = {
          new PropertyAccessor {
            val payload = pl
            def apply[T](property: Symbol): Future[T] = {
              propertyAccessors.get(this) match {
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
        }
      }

      val render = initRender(korolevAccess)

      val onState: State => Unit = { state =>
        println("RRT: " + (System.currentTimeMillis() - reduceRealT) / 1000d)
        val tr = System.currentTimeMillis()
        val newRender = render(state)
        println("render: " + (System.currentTimeMillis() - tr) / 1000d)
        val t = System.currentTimeMillis()
        val changes = VDom.changes(lastRender, newRender)
        val prevRender = lastRender
        println("diff: " + (System.currentTimeMillis() - t) / 1000d)
        lastRender = newRender

        val scT = System.currentTimeMillis()
        changes foreach {
          case Create(id, childId, tag) =>
            korolevJS.call("Create", id.toString, childId.toString, tag)
          case CreateText(id, childId, text) =>
            korolevJS.call("CreateText", id.toString, childId.toString, text)
          case Remove(id, childId) =>
            //println(prevRender(childId).misc)
            prevRender(childId).toList.flatMap(_.misc) foreach {
              case pa: PropertyAccessor => propertyAccessors.remove(pa)
              case event: Event => events.remove(s"$childId:${event.`type`}")
            }
            korolevJS.call("Remove", id.toString, childId.toString)
          case SetAttr(id, name, value, isProperty) =>
            korolevJS.call("SetAttr", id.toString, name, value, isProperty)
          case RemoveAttr(id, name, isProperty) =>
            korolevJS.call("RemoveAttr", id.toString, name, isProperty)
          case AddMisc(id, event: Event) =>
            events.put(s"$id:${event.`type`}:${event.phase}", event)
          case RemoveMisc(id, event: Event) =>
            val typeAndId = s"$id:${event.`type`}:${event.phase}"
            events.remove(typeAndId)
          case AddMisc(id, pa: PropertyAccessor) =>
            propertyAccessors.put(pa, id.toString)
          case RemoveMisc(id, pa: PropertyAccessor) =>
            propertyAccessors.remove(pa)
          case _ =>
          // do nothing
        }
        println("SCT: " + (System.currentTimeMillis() - scT) / 1000d)
        jsAccess.flush()
      }

      dux.subscribe(onState)
      onState(initialState)
    }

    jsAccess.flush()
    dux
  }
}
