package korolev

import korolev.VDom.Id

import scala.annotation.tailrec
import scala.language.higherKinds
import scala.util.{Failure, Success}

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
trait EventPropagation {

  import BrowserEffects._
  import EventPhase._

  def propagateEvent[F[_]: Async, S](events: collection.Map[String, Event[F, S]],
                                     dux: Dux.Transition[S] => F[Unit],
                                     browserAccess: BrowserAccess[F],
                                     target: Id,
                                     tpe: String): Unit = {

    def fire(event: Event[F, S]): Boolean = {
      val EventResult(it, dt, haveToStop) = event match {
        case EventWithAccess(_, _, effect) => effect(browserAccess)
        case SimpleEvent(_, _, effect) => effect()
      }
      Seq(it.map(Async[F].pure[Dux.Transition[S]](_)), dt).flatten foreach { m =>
        Async[F].run(m) {
          case Success(t) => dux(t)
          case Failure(e) => e.printStackTrace()
        }
      }
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
