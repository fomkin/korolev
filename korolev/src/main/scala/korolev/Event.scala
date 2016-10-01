package korolev

import korolev.VDom.Misc

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
trait Event extends Misc {

  def payload: Any
  def fire(): Boolean
  def `type`: String
  def phase: Event.Phase

  override def equals(obj: Any): Boolean = obj match {
    case event: Event =>
      event.payload == payload &&
      event.`type` == `type` &&
      event.phase == phase
    case _ => false
  }

  override def toString: String = {
    s"Event(${`type`}, $payload)"
  }
}

object Event {
  sealed trait Phase
  case object Capturing extends Phase
  case object AtTarget extends Phase
  case object Bubbling extends Phase
}

