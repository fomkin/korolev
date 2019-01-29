package korolev.catsEffectSupport

import cats.effect.IO
import korolev.Async

import scala.collection.generic.CanBuildFrom
import scala.util.Try
import cats.syntax.applicative._
import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.syntax.applicativeError._
import cats.effect.syntax.effect._

import scala.collection.{TraversableOnce, mutable}

object implicits {
  implicit def korolevAsyncFromEffect[F[_]](implicit F: cats.effect.Effect[F]): Async[F] = new Async[F] {
    override def pureStrict[A](value: A): F[A] =
      value.pure

    override def pure[A](value: => A): F[A] =
      F.delay(value)

    override def fork[A](value: => A): F[A] =
      F.delay(value)

    override def unit: F[Unit] =
      F.unit

    override def fromTry[A](value: => Try[A]): F[A] =
      F.fromTry(value)

    override def promise[A]: Async.Promise[F, A] = {
      val promise = scala.concurrent.Promise[A]()
      new korolev.Async.Promise[F, A] {
        val async: F[A] = F.liftIO(IO.fromFuture(IO.pure(promise.future)))
        override def complete(`try`: Try[A]): Unit = {
          promise.complete(`try`)
          ()
        }
        override def completeAsync(async: F[A]): Unit = {
          promise.completeWith(async.toIO.unsafeToFuture())
          ()
        }
      }
    }

    override def flatMap[A, B](m: F[A])(f: A => F[B]): F[B] =
      m.flatMap(f)

    override def map[A, B](m: F[A])(f: A => B): F[B] = m.map(f)

    override def recover[A, U >: A](m: F[A])(f: PartialFunction[Throwable, U]): F[U] =
      m.map[U](identity).recover(f)

    override def sequence[A, M[X] <: TraversableOnce[X]](in: M[F[A]])
                                                        (implicit cbf: CanBuildFrom[M[F[A]], A, M[A]]): F[M[A]] = {
      def loop(cursor: Iterator[F[A]], acc: mutable.Builder[A, M[A]]): F[M[A]] = {
        if (cursor.hasNext) {
          val next = cursor.next()
          next.flatMap { a => loop(cursor, acc += a) }
        } else acc.result().pure
      }
      F.defer {
        val cursor: Iterator[F[A]] = in.toIterator
        loop(cursor, cbf(in))
      }
    }

    override def run[A, U](m: F[A])(f: Try[A] => U): Unit =
      m.runAsync { r =>
        IO {
          f(r.toTry)
          ()
        }
      }.unsafeRunSync()
  }
}
