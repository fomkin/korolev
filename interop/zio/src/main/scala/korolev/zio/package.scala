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

package korolev

import korolev.effect.Effect
import _root_.zio.{Runtime, Task, ZIO}

package object zio {

  /**
    * Provides [[Effect]] instance for ZIO[Any, Throwable, *].
    * Use this method if your app uses [[Throwable]] to express errors.
    */
  def taskEffectInstance[R](runtime: Runtime[R]): Effect[Task] =
    new ZioEffect[Any, Throwable](runtime, identity, identity)

  /**
    * Provides [[Effect]] instance for ZIO with arbitrary runtime
    * and error types. Korolev uses Throwable inside itself.
    * That means if you want to work with your own [[E]],
    * you should provide functions to convert [[Throwable]]
    * to [[E]] and vice versa.
    *
    * {{{
    *   sealed trait MyError
    *   object MyError {
    *     case class UserNotFound(id: Long) extends MyError
    *     case object DoNotLikeIt extends MyError
    *     case class Unexpected(e: Throwable) extends MyError
    *   }
    *   case class MyErrorException(error: MyError) extends Throwable
    *
    *   val runtime = new DefaultRuntime {}
    *   implicit val zioEffect = korolev.zio.zioEffectInstance(runtime)(MyError.Unexpected)(MyErrorException)
    *
    *   val ctx = Context[IO[MyError, *], MyState, Any]
    * }}}
    */
  final def zioEffectInstance[R, E](runtime: Runtime[R])
                                   (liftError: Throwable => E)
                                   (unliftError: E => Throwable): Effect[ZIO[R, E, *]] =
    new ZioEffect[R, E](runtime, liftError, unliftError)
}
