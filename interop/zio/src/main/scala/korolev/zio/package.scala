package korolev

import korolev.effect.Effect
import _root_.zio.{Runtime, Task, ZIO}

package object zio {

  /**
    * Provides [[Effect]] instance for ZIO[Any, Throwable, *].
    * Use this method if your app uses [[Throwable]] to express errors.
    */
  def taskEffectInstance[R](runtime: Runtime[R]): Effect[Task] =
    new ZioEffect[Any, Throwable](runtime, identity, identity)

  /**
    * Provides [[Effect]] instance for ZIO with arbitrary runtime
    * and error types. Korolev uses Throwable inside itself.
    * That means if you want to work with your own [[E]],
    * you should provide functions to convert [[Throwable]]
    * to [[E]] and vice versa.
    */
  final def zioEffectInstance[R, E](runtime: Runtime[R])
                                   (liftError: Throwable => E)
                                   (unliftError: E => Throwable): Effect[ZIO[R, E, *]] =
    new ZioEffect[R, E](runtime, liftError, unliftError)
}
