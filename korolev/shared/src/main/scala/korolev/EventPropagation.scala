package korolev

import korolev.StateManager.Transition
import korolev.VDom.Id

import scala.annotation.tailrec
import scala.language.higherKinds
import scala.util.{Failure, Success}
import slogging.LazyLogging

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
trait EventPropagation extends LazyLogging {

  import Effects._
  import EventPhase._

  def propagateEvent[F[+_]: Async, S, M](events: collection.Map[String, Event[F, S, M]],
                                     stateManager: StateManager.Transition[S] => F[Unit],
                                     browserAccess: Access[F, S, M],
                                     target: Id,
                                     tpe: String): Unit = {

    def fire(event: Event[F, S, M]): Boolean = {
      def applyTransitions(transitionsFs: List[F[Transition[S]]]): Unit = transitionsFs match {
        case f :: fs =>
          Async[F].run(Async[F].flatMap(f)(t => stateManager(t))) {
            case Success(_) => applyTransitions(fs)
            case Failure(e) => logger.error("Error occurred during browser event handling", e)
          }
        case Nil =>
      }
      val EventResult(it, dt, haveToStop) = event match {
        case EventWithAccess(_, _, effect) => effect(browserAccess)
        case SimpleEvent(_, _, effect) => effect()
      }
      val transitionFs = List(it.map(Async[F].pure[StateManager.Transition[S]](_)), dt).flatten
      applyTransitions(transitionFs)
      !haveToStop
    }

    @tailrec def capture(acc: List[Id], i: Int, v: Vector[Int]): List[Id] = {
      if (i == v.length) {
        acc
      } else {
        val id = Id(v.take(i))
        val continue = events.get(s"$id:$tpe:$Capturing") match {
          case None => true
          case Some(event) => fire(event)
        }
        if (continue) capture(id :: acc, i + 1, v)
        else Nil
      }
    }

    @tailrec def bubbling(list: List[Id]): Unit = list match {
      case Nil =>
      case x :: xs =>
        val continue = events.get(s"$x:$tpe:$Bubbling") match {
          case None => true
          case Some(event) => fire(event)
        }
        if (continue)
          bubbling(xs)
    }

    val captured = capture(Nil, 1, target.vec)

    if (captured.nonEmpty) {
      events.get(s"$target:$tpe:$AtTarget") match {
        case Some(event) =>
          if (fire(event)) bubbling(captured)
        case None => bubbling(target :: captured)
      }
    }
  }
}
