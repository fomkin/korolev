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

import cats.Traverse
import cats.effect._
import cats.instances.list._
import korolev.effect.Effect

import scala.concurrent.Future
import scala.util.Try

object implicits {

  implicit def korolevAsyncFromEffect[F[_]: Effect]: Effect[F] = new Effect[F] {

    def pure[A](value: A): F[A] =
      Effect[F].pure(value)

    def delay[A](value: => A): F[A] =
      Effect[F].delay(value)

    def fork[A](value: => A): F[A] =
      Effect[F].delay(value)

    def unit: F[Unit] =
      Effect[F].unit

    def fromTry[A](value: => Try[A]): F[A] =
      Effect[F].fromTry(value)

    def promise[A]: Effect.Promise[F, A] = {
      new korolev.Effect.Promise[F, A] {

        private var callback: Either[Throwable, A] => Unit = _

        val async: F[A] = Effect[F].async { cb =>
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
          Effect[F].runAsync(async)(result => IO.pure(callback(result)))
            .unsafeRunSync()
        }
      }
    }

    def flatMap[A, B](m: F[A])(f: A => F[B]): F[B] =
      Effect[F].flatMap(m)(f)

    def map[A, B](m: F[A])(f: A => B): F[B] =
      Effect[F].map(m)(f)

    def recover[A](m: F[A])(f: PartialFunction[Throwable, A]): F[A] =
      Effect[F].recover(m)(f)

    def sequence[A](in: List[F[A]]): F[List[A]] =
      Traverse[List].sequence(in)

    def runAsync[A, U](m: F[A])(callback: Try[A] => U): Unit = {
      // FIXME async is always sync, because no context shift by default
      Effect[F]
        .runAsync(m)(result => IO { callback(result.toTry); () })
        .unsafeRunSync()
    }

    def toFuture[A](m: F[A]): Future[A] =
      Effect[F].toIO(m).unsafeToFuture()
  }
}
