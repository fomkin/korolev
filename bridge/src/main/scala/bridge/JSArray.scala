package bridge

import korolev.Async

/**
 * JavaScript Array representation.
 */
abstract class JSArray[F[+_]: Async] extends JSObj[F] {

  def apply[A](i: Int): F[A] = {
    jsAccess.request("get", this, i)
  }

  def update(i: Int, value: Any): F[Unit] = {
    jsAccess.request("set", this, i, value)
  }
}
