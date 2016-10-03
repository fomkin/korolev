package korolev

import korolev.VDom.Misc

import scala.concurrent.Future

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
trait ElementAccessor extends Misc {
  def get[T](name: Symbol): Future[T]
}
