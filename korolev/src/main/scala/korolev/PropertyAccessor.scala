package korolev

import korolev.VDom.Misc

import scala.concurrent.Future

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
trait PropertyAccessor extends Misc {
  def apply[T](name: Symbol): Future[T]
}
