package korolev

import korolev.VDom.Misc

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
trait Event extends Misc {

  def payload: Any
  def fire(): Unit
  def `type`: String

  override def equals(obj: Any): Boolean = obj match {
    case event: Event => event.payload == payload
    case _ => false
  }
}

