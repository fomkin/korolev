/*
 * Copyright 2017-2018 Aleksey Fomkin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package korolev.catsEffectSupport

import cats.effect.IO
import korolev.Async

import scala.collection.generic.CanBuildFrom
import scala.util.{Failure, Success, Try}
import cats.syntax.applicative._
import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.syntax.applicativeError._
import cats.effect.syntax.effect._

import scala.collection.{TraversableOnce, mutable}

object implicits {
  implicit def korolevAsyncFromEffect[F[_]](implicit F: cats.effect.Effect[F]): Async[F] = new Async[F] {
    override def pure[A](value: A): F[A] =
      value.pure

    override def delay[A](value: => A): F[A] =
      F.delay(value)

    override def fork[A](value: => A): F[A] =
      F.delay(value)

    override def unit: F[Unit] =
      F.unit

    override def fromTry[A](value: => Try[A]): F[A] =
      F.fromTry(value)

    def promise[A]: Async.Promise[F, A] = {
      new korolev.Async.Promise[F, A] {

        private var callback: Either[Throwable, A] => Unit = _

        val async: F[A] = F.async { cb =>
          this.synchronized {
            callback = cb
            this.notify()
          }
        }

        def complete(`try`: Try[A]): Unit = this.synchronized {
          if (callback == null)
            this.wait()
          callback(`try`.toEither)
        }

        def completeAsync(async: F[A]): Unit = {
          F.runAsync(async)(result => IO.pure(callback(result)))
            .unsafeRunSync()
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
          f(r.fold(Failure(_), Success(_))) // for scala 2.11
          ()
        }
      }.unsafeRunSync()
  }
}
