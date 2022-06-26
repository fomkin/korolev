/*
 * Copyright 2017-2020 Aleksey Fomkin
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

package korolev.zio

import _root_.zio.{Runtime, ZIO, Unsafe}
import korolev.effect.Effect

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class Zio2Effect[R, E](rts: Runtime[R],
                       liftError: Throwable => E,
                       unliftError: E => Throwable) extends Effect[ZIO[R, E, *]] {

  private val unliftErrorP: PartialFunction[E, Throwable] =
    { case x => unliftError(x) }

  def pure[A](value: A): ZIO[R, E, A] =
    ZIO.succeed(value)

  def delay[A](value: => A): ZIO[R, E, A] =
    ZIO.attempt(value).mapError(liftError)

  def fail[A](e: Throwable): ZIO[R, E, A] =
    ZIO.fail(e).mapError(liftError)

  def unit: ZIO[R, E, Unit] =
    ZIO.unit

  def never[T]: ZIO[R, E, T] =
    ZIO.never

  def fromTry[A](value: => Try[A]): ZIO[R, E, A] =
    ZIO.fromTry(value).mapError(liftError)

  def promise[A](callback: (Either[Throwable, A] => Unit) => Unit): ZIO[R, E, A] =
    ZIO.async { register =>
      callback { either =>
        register {
          either match {
            case Left(error) => ZIO.fail(liftError(error))
            case Right(value) => ZIO.succeed(value)
          }
        }
      }
    }

  def promiseF[A](callback: (Either[Throwable, A] => Unit) => ZIO[R, E, Unit]): ZIO[R, E, A] =
    ZIO.asyncZIO { register =>
      callback { either =>
        register {
          either match {
            case Left(error) => ZIO.fail(liftError(error))
            case Right(value) => ZIO.succeed(value)
          }
        }
      }
    }

  def flatMap[A, B](m: ZIO[R, E, A])(f: A => ZIO[R, E, B]): ZIO[R, E, B] =
    m.flatMap(f)

  def map[A, B](m: ZIO[R, E, A])(f: A => B): ZIO[R, E, B] =
    m.map(f)

  def recover[A, AA >: A](m: ZIO[R, E, A])(f: PartialFunction[Throwable, AA]): ZIO[R, E, AA] =
    m.catchSome(unliftErrorP.andThen(f).andThen(result => ZIO.succeed(result)))

  def recoverF[A, AA >: A](m: ZIO[R, E, A])(f: PartialFunction[Throwable, ZIO[R, E, AA]]): ZIO[R, E, AA] =
    m.catchSome(unliftErrorP.andThen(f).andThen(result => result))

  def start[A](task: => ZIO[R, E, A])(implicit ec: ExecutionContext): ZIO[R, E, Effect.Fiber[ZIO[R, E, *], A]] =
    ZIO
      .interruptible(task.mapError(unliftError))
      .forkDaemon
      .map { fiber =>
        new Effect.Fiber[ZIO[R, E, *], A] {
          def join(): ZIO[R, E, A] =
            fiber.join.mapError(liftError)
        }
      }

  def fork[A](m: => ZIO[R, E, A])(implicit ec: ExecutionContext): ZIO[R, E, A] =
    m.onExecutionContext(ec)

  def sequence[A](in: List[ZIO[R, E, A]]): ZIO[R, E, List[A]] =
    ZIO.collectAll(in)

  def runAsync[A](m: ZIO[R, E, A])(callback: Either[Throwable, A] => Unit): Unit =
    Unsafe.unsafeCompat { implicit u: Unsafe =>
      rts
        .unsafe
        .fork(m)
        .unsafe
        .addObserver(exit => callback(exit.toEither))
    }

  def run[A](m: ZIO[R, E, A]): Either[Throwable, A] =
    Unsafe.unsafeCompat { implicit u: Unsafe =>
      rts
        .unsafe
        .run(m)
        .toEither
    }

  def toFuture[A](m: ZIO[R, E, A]): Future[A] =
    Unsafe.unsafeCompat { implicit u: Unsafe =>
      val unlifted = m.mapError(unliftError)
      rts.unsafe.runToFuture(unlifted)
    }
}
