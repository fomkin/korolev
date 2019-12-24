package korolev.effect

import korolev.effect.Effect.Fiber

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

object syntax {

  implicit final class ListEffectOps[F[_]: Effect, A](effects: List[F[A]]) {
    def sequence: F[List[A]] = Effect[F].sequence(effects)
  }

  implicit final class EffectOps[F[_]: Effect, A](effect: F[A]) {
    def map[B](f: A => B): F[B] = Effect[F].map(effect)(f)
    def unit: F[Unit] = Effect[F].map(effect)(_ => ())
    def flatMap[B](f: A => F[B]): F[B] = Effect[F].flatMap(effect)(f)
    def recover(f: PartialFunction[Throwable, A]): F[A] = Effect[F].recover[A](effect)(f)
    def start()(implicit ec: ExecutionContext): F[Fiber[F, A]] = Effect[F].start(effect)
    def runAsync[U](f: Try[A] => U): Unit = Effect[F].runAsync(effect)(f)
    def runAsyncSuccess[U](f: A => U)(implicit er: Reporter): Unit =
      Effect[F].runAsync(effect) {
        case Success(x) => f(x)
        case Failure(e) => er.error("Unhandled error", e)
      }
    def runAsyncForget(implicit er: Reporter): Unit =
      Effect[F].runAsync(effect) {
        case Success(_) => // do nothing
        case Failure(e) => er.error("Unhandled error", e)
      }
  }
}
