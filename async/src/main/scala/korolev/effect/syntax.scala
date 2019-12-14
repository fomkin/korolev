package korolev.effect

import scala.util.{Failure, Success, Try}

object syntax {

  implicit final class EffectOps[F[_]: Effect, A](async: F[A]) {
    def map[B](f: A => B): F[B] = Effect[F].map(async)(f)
    def flatMap[B](f: A => F[B]): F[B] = Effect[F].flatMap(async)(f)
    def recover(f: PartialFunction[Throwable, A]): F[A] = Effect[F].recover[A](async)(f)
    def run[U](f: Try[A] => U): Unit = Effect[F].runAsync(async)(f)
    def runOrReport[U](f: A => U)(implicit er: Reporter): Unit =
      Effect[F].runAsync(async) {
        case Success(x) => f(x)
        case Failure(e) => er.error("Unhandled error", e)
      }
    def runIgnoreResult(implicit er: Reporter): Unit =
      Effect[F].runAsync(async) {
        case Success(_) => // do nothing
        case Failure(e) => er.error("Unhandled error", e)
      }
  }
}
