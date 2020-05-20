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

import java.util.concurrent.atomic.AtomicReference

final class Var[F[_]: Effect, T](initialValue: T) {
  private val casRef = new AtomicReference[T](initialValue)
  def set(value: T): F[Unit] =
    Effect[F].delay(casRef.set(value))
  def get: F[T] =
    Effect[F].delay(casRef.get)
  def compareAndSet(expected: T, value: T): F[Boolean] =
    Effect[F].delay(casRef.compareAndSet(expected, value))
}

object Var {
  def apply[F[_]: Effect, T](initialValue: T): Var[F, T] =
    new Var(initialValue)
}
