package korolev.effect


import cats.effect
import cats.effect.{ExitCase, IO, SyncIO}
import cats.effect.laws.discipline.EffectTests
import cats.effect.laws.discipline.arbitrary._
import cats.effect.laws.util.TestInstances._
import org.scalacheck.Arbitrary
import org.scalatest.funsuite.AnyFunSuite
import org.scalatestplus.scalacheck.Checkers
import org.typelevel.discipline.scalatest.FunSuiteDiscipline
import cats.kernel.Eq
import cats.implicits._
import cats.effect.implicits._
import cats.effect.laws.util.{TestContext, TestInstances}
import cats.laws.discipline.{ApplicativeTests, MonadTests, SemigroupalTests}

import scala.util.{Failure, Success, Try}

class BrightFutureLawTest extends AnyFunSuite
  with Checkers
  with FunSuiteDiscipline
  with TestInstances {

  implicit val tc: TestContext = TestContext()

  implicit def arbFuture[A: Arbitrary]: Arbitrary[BrightFuture[A]] =
    Arbitrary(Arbitrary.arbitrary[A].map(x => BrightFuture.Pure(x)))

  implicit def futureCatsEq[T: Eq]: cats.Eq[BrightFuture[T]] =
    new Eq[BrightFuture[T]] {
      def eqv(x: BrightFuture[T], y: BrightFuture[T]): Boolean =
        eqFuture[T].eqv(x, y)
    }

//  implicit val throwableEq: Eq[Throwable] =
//    Eq.by[Throwable, String](_.toString)

  implicit object FutureCatsEffect extends effect.Effect[BrightFuture] {
    final def runAsync[A](fa: BrightFuture[A])(cb: Either[Throwable, A] => IO[Unit]): SyncIO[Unit] =
      SyncIO {
        val cb2 = cb
          .compose[Try[A]](_.toEither)
          .andThen[IO[Unit]](_ => IO.unit)
        fa.run(RunNowExecutionContext)(cb2)
      }
    final def async[A](k: (Either[Throwable, A] => Unit) => Unit): BrightFuture[A] =
      BrightFuture.Async(k)
    final def asyncF[A](k: (Either[Throwable, A] => Unit) => BrightFuture[Unit]): BrightFuture[A] =
      BrightFuture.Async(k.andThen(_.run(RunNowExecutionContext)(_ => ())))
    final def suspend[A](thunk: => BrightFuture[A]): BrightFuture[A] =
      BrightFuture.Bind[Unit, A](BrightFuture.unit, _ => thunk, RunNowExecutionContext)
    final def raiseError[A](e: Throwable): BrightFuture[A] =
      BrightFuture.Error(e)
    final def handleErrorWith[A](fa: BrightFuture[A])(f: Throwable => BrightFuture[A]): BrightFuture[A] =
      BrightFuture.Bind[A, A](fa, {
        case Success(value) => BrightFuture.Pure(value)
        case Failure(exception) => f(exception)
      }, RunNowExecutionContext)
    final def pure[A](x: A): BrightFuture[A] =
      BrightFuture.Pure(x)
    final def flatMap[A, B](fa: BrightFuture[A])(f: A => BrightFuture[B]): BrightFuture[B] =
      BrightFuture.Bind[A, B](fa, {
        case Success(value) => f(value)
        case Failure(exception) => BrightFuture.Error(exception)
      }, RunNowExecutionContext)
    final def tailRecM[A, B](a: A)(f: A => BrightFuture[Either[A, B]]): BrightFuture[B] =
      flatMap(f(a)) {
        case Left(a)  => tailRecM(a)(f)
        case Right(b) => pure(b)
      }
    final def bracketCase[A, B](acquire: BrightFuture[A])(use: A => BrightFuture[B])(release: (A, ExitCase[Throwable]) => BrightFuture[Unit]): BrightFuture[B] = {
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

  //checkAll("BrightFuture.Applicative", ApplicativeTests[BrightFuture].applicative[Int, Int, String])
  checkAll("BrightFuture.Monad", MonadTests[BrightFuture].monad[Int, Int, String])
  //checkAll("BrightFuture.EffectLaws", EffectTests[BrightFuture].effect[Int, Int, String])
}
