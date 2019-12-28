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

package korolev.effect

object syntax {

  implicit final class ListEffectOps[F[_]: Effect, A](effects: List[F[A]]) {
    def sequence: F[List[A]] = Effect[F].sequence(effects)
  }

  implicit final class EffectOps[F[_]: Effect, A](effect: F[A]) {

    def map[B](f: A => B): F[B] = Effect[F].map(effect)(f)

    /**
      * Alias for {{{.flatMap(_ => ())}}}
      */
    def unit: F[Unit] = Effect[F].map(effect)(_ => ())

    def flatMap[B](f: A => F[B]): F[B] = Effect[F].flatMap(effect)(f)

    /**
      * Alias for [[after]]
      */
    def >>[B](m: => F[B]): F[B] = Effect[F].flatMap(effect)(_ => m)

    /**
      * Do 'm' right after [[effect]]
      */
    def after[B](m: => F[B]): F[B] = Effect[F].flatMap(effect)(_ => m)

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
