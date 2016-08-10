package bridge

import scala.concurrent.Future
import scala.language.dynamics

/**
 * JavaScript Array presentation
 * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
 */
trait JSArray extends JSObj {

  def apply[A](i: Int): Future[A] = {
    jsAccess.request("get", this, i)
  }

  def update(i: Int, value: Any): Future[Unit] = {
    jsAccess.request("set", this, i, value)
  }
}
