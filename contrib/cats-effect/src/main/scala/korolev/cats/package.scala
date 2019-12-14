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

package korolev

import _root_.cats.Traverse
import _root_.cats.effect._
import _root_.cats.instances.list._

import korolev.effect.{Effect => KEffect}

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Try

package object cats {

  implicit object IOEffect extends KEffect[IO] {

    def pure[A](value: A): IO[A] =
      IO.pure(value)

    def delay[A](value: => A): IO[A] =
      IO.delay(value)

    // TODO
    def fork[A](value: => A): IO[A] =
      IO.delay(value)

    def unit: IO[Unit] =
      IO.unit

    def fromTry[A](value: => Try[A]): IO[A] =
      IO.fromTry(value)

    def promise[A]: KEffect.Promise[IO, A] = {
      new KEffect.Promise[IO, A] {

        private var callback: Either[Throwable, A] => Unit = _
        val effect: IO[A] = {
          val io: IO[A] = IO.async(cb => callback = cb)
          io.unsafeRunAsyncAndForget()
          io
        }

        def complete(`try`: Try[A]): Unit = {
          callback(`try`.toEither)
        }

        def completeAsync(async: IO[A]): Unit = {
          async.unsafeRunAsync(callback)
        }
      }
    }

    def flatMap[A, B](m: IO[A])(f: A => IO[B]): IO[B] =
      m.flatMap(f)

    def map[A, B](m: IO[A])(f: A => B): IO[B] =
      m.map(f)

    def recover[A](m: IO[A])(f: PartialFunction[Throwable, A]): IO[A] =
      m.handleErrorWith(e => f.andThen(IO.pure[A]).applyOrElse(e, IO.raiseError[A]))

    def sequence[A](in: List[IO[A]]): IO[List[A]] =
      Traverse[List].sequence(in)

    def runAsync[A, U](m: IO[A])(callback: Try[A] => U): Unit = {
      val toTry: Either[Throwable, A] => Try[A] = _.toTry
      val cb: Either[Throwable, A] => Unit = callback.compose(toTry).andThen(_ => ())
      m.unsafeRunAsync(cb)
    }

    def run[A](m: IO[A], timeout: Duration): Option[A] = {
      m.unsafeRunTimed(timeout)
    }

    def toFuture[A](m: IO[A]): Future[A] =
      m.unsafeToFuture()
  }
}
