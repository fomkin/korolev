package korolev

import korolev.VDom.Misc

import scala.concurrent.Future

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
trait PropertyAccessor extends Misc {

  def payload: Any
  def apply[T](name: Symbol): Future[T]

  override def equals(obj: scala.Any): Boolean = obj match {
    case accessor: PropertyAccessor => accessor.payload == payload
    case _ => false
  }

  override def toString: String = {
    s"PropertyAccessor($payload)"
  }
}
