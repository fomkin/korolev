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

package korolev.execution

import korolev.Async

import scala.annotation.implicitNotFound
import scala.concurrent.duration.FiniteDuration

@implicitNotFound("Scheduler for ${F} is not found. Ensure that it is passed to the scope (import korolev.execution.defaultScheduler)")
abstract class Scheduler[F[_] : Async] {
  import Scheduler._
  def scheduleOnce[T](delay: FiniteDuration)(job: => T): JobHandler[F, T]
  def schedule[U](interval: FiniteDuration)(job: => U): Cancel
}

object Scheduler {

  def apply[F[_]: Scheduler]: Scheduler[F] = implicitly[Scheduler[F]]

  type Cancel = () => Unit

  case class JobHandler[F[_]: Async, T](
    cancel: Cancel,
    result: F[T]
  )
}

