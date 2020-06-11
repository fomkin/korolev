package korolev.effect

import cats.effect
import cats.effect.{ExitCase, IO, SyncIO}
import cats.effect.laws.EffectLaws
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class BrightFutureSpec extends FlatSpec with Matchers {
  class FutureCatsEffect extends effect.Effect[Future] {
    final def runAsync[A](fa: Future[A])(cb: Either[Throwable, A] => IO[Unit]): SyncIO[Unit] =
      SyncIO {
        val cb2 = cb
          .compose[Try[A]](_.toEither)
          .andThen[IO[Unit]](_ => IO.unit)
        fa.run(RunNowExecutionContext)(cb2)
      }
    final def async[A](k: (Either[Throwable, A] => Unit) => Unit): Future[A] =
      BrightFuture.Async(k)
    final def asyncF[A](k: (Either[Throwable, A] => Unit) => Future[Unit]): Future[A] =
      BrightFuture.Async(k.andThen(_.run(RunNowExecutionContext)(_ => ())))
    final def suspend[A](thunk: => Future[A]): Future[A] =
      BrightFuture.Bind(BrightFuture.unit, _ => thunk, RunNowExecutionContext)
    final def raiseError[A](e: Throwable): Future[A] =
      BrightFuture.Error(e)
    final def handleErrorWith[A](fa: Future[A])(f: Throwable => Future[A]): Future[A] =
      BrightFuture.Bind[A, A](fa, {
        case Success(value) => BrightFuture.Pure(value)
        case Failure(exception) => f(exception)
      }, RunNowExecutionContext)
    final def pure[A](x: A): Future[A] =
      BrightFuture.Pure(x)
    final def flatMap[A, B](fa: Future[A])(f: A => Future[B]): Future[B] =
      BrightFuture.Bind(fa, {
        case Success(value) => f(value)
        case Failure(exception) => BrightFuture.Error(exception)
      }, RunNowExecutionContext)
    final def tailRecM[A, B](a: A)(f: A => Future[Either[A, B]]): Future[B] =
      flatMap(f(a)) {
        case Left(a)  => tailRecM(a)(f)
        case Right(b) => pure(b)
      }
    final def bracketCase[A, B](acquire: Future[A])(use: A => Future[B])(release: (A, ExitCase[Throwable]) => Future[Unit]): Future[B] = {
      flatMap(acquire) { a =>
        flatMap(use(a).attempt) { etb =>
          flatMap(release(a, etb match {
            case Left(e) => ExitCase.error[Throwable](e)
            case Right(_) => ExitCase.complete
          })) { _ =>
            etb match {
              case Left(e) => BrightFuture.Error(e)
              case Right(v) => BrightFuture.Pure(v)
            }
          }
        }
      }
    }
  }
}
