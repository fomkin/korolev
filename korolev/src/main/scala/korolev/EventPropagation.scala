package korolev

import korolev.VDom.Id

import scala.annotation.tailrec

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
trait EventPropagation {

  import Event._

  def propagateEvent(events: collection.Map[String, Event], target: Id, tpe: String): Unit = {

    @tailrec def capture(acc: List[Id], i: Int, v: Vector[Int]): List[Id] = {
      if (i == v.length) {
        acc
      } else {
        val id = Id(v.take(i))
        val continue = events.get(s"$id:$tpe:$Capturing") match {
          case None => true
          case Some(event) => event.fire()
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
          case Some(event) => event.fire()
        }
        if (continue)
          bubbling(xs)
    }

    val captured = capture(Nil, 1, target.vec)

    if (captured.nonEmpty) {
      events.get(s"$target:$tpe:$AtTarget") match {
        case Some(event) =>
          if (event.fire())
            bubbling(captured)
        case None => bubbling(target :: captured)
      }
    }
  }
}
