package korolev

import korolev.effect.Effect
import _root_.zio.{RIO, Task, Runtime}

package object zio {
  implicit final def taskEffectInstance[R](implicit runtime: Runtime[R]): Effect[Task] =
    new ZioEffect[R](runtime)
}
