package korolev.effect

import scala.concurrent.ExecutionContext

object syntax {

  implicit final class ListEffectOps[F[_]: Effect, A](effects: List[F[A]]) {
    def sequence: F[List[A]] = Effect[F].sequence(effects)
  }

  implicit final class EffectOps[F[_]: Effect, A](effect: F[A]) {
    def map[B](f: A => B): F[B] = Effect[F].map(effect)(f)
    def unit: F[Unit] = Effect[F].map(effect)(_ => ())
    def flatMap[B](f: A => F[B]): F[B] = Effect[F].flatMap(effect)(f)
    def recover(f: PartialFunction[Throwable, A]): F[A] = Effect[F].recover[A](effect)(f)
    def runAsync(f: Either[Throwable, A] => Unit): Unit = Effect[F].runAsync(effect)(f)
    def runAsyncSuccess(f: A => Unit)(implicit er: Reporter): Unit =
      Effect[F].runAsync(effect) {
        case Right(x) => f(x)
        case Left(e) => er.error("Unhandled error", e)
      }
    def runAsyncForget(implicit er: Reporter): Unit =
      Effect[F].runAsync(effect) {
        case Right(_) => // do nothing
        case Left(e) => er.error("Unhandled error", e)
      }
  }
}
