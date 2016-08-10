package korolev

import korolev.VDom.Misc

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
trait Event extends Misc {
  def fire(): Unit
  def `type`: String
}

