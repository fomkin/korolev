package bridge

import scala.language.higherKinds
import korolev.Async

/**
 * JavaScript Array presentation
 * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
 */
abstract class JSArray[F[+_]: Async] extends JSObj[F] {

  def apply[A](i: Int): F[A] = {
    jsAccess.request("get", this, i)
  }

  def update(i: Int, value: Any): F[Unit] = {
    jsAccess.request("set", this, i, value)
  }
}
