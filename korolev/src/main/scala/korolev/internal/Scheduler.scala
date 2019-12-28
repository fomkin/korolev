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

package korolev.internal

import java.util.{Timer, TimerTask}

import korolev.effect.Effect.Promise
import korolev.effect.syntax._
import korolev.effect.Effect

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

private[korolev] final class Scheduler[F[_]: Effect](implicit ec: ExecutionContext) {

  import Scheduler._

  private val timer = new Timer()

  def scheduleOnce[T](delay: FiniteDuration)(job: => F[T]): JobHandler[F, T] =
    new JobHandler[F, T] {

      @volatile private var completed: Either[Throwable, T] = _
      @volatile private var promise: Promise[T] = _

      private val task = new TimerTask {
        def run(): Unit = {
          Effect[F]
            .fork(job)
            .runAsync { errorOrResult =>
              if (promise != null) promise(errorOrResult)
              else completed = errorOrResult
            }
        }
      }

      def result: F[T] = Effect[F].promise { cb =>
        if (completed != null) cb(completed)
        else promise = cb
      }
      def cancelUnsafe(): Unit = {
        task.cancel()
        ()
      }

      timer.schedule(task, delay.toMillis)
    }
}

object Scheduler {

  trait JobHandler[F[_], T] {
    def cancelUnsafe(): Unit
    def result: F[T]
  }
}
