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

import java.util.{Timer, TimerTask}

import korolev.effect.Effect.Promise
import korolev.effect.syntax._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

final class Scheduler[F[_]: Effect](implicit ec: ExecutionContext) {

  import Scheduler._

  private val timer = new Timer()

  def schedule(delay: FiniteDuration): F[Stream[F, Unit]] = Effect[F].delay {
    new Stream[F, Unit] {
      var canceled = false
      var cb: Either[Throwable, Option[Unit]] => Unit = _
      var task: TimerTask = _
      def pull(): F[Option[Unit]] = Effect[F].promise { cb =>
        if (canceled) cb(Right(None)) else {
          this.cb = cb
          this.task = new TimerTask { def run(): Unit = cb(Right(Some(()))) }
          timer.schedule(task, delay.toMillis)
        }
      }
      def cancel(): F[Unit] = Effect[F].delay {
        if (task != null) {
          canceled = true
          task.cancel()
          task = null
          cb(Right(None))
        }
      }
    }
  }

  def sleep(delay: FiniteDuration): F[Unit] = Effect[F].promise { cb =>
    val task: TimerTask = new TimerTask { def run(): Unit = cb(Right(())) }
    timer.schedule(task, delay.toMillis)
  }

  def scheduleOnce[T](delay: FiniteDuration)(job: => F[T]): F[JobHandler[F, T]] =
    Effect[F].delay(unsafeScheduleOnce(delay)(job))

  def unsafeScheduleOnce[T](delay: FiniteDuration)(job: => F[T]): JobHandler[F, T] =
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

      def cancel(): F[Unit] = Effect[F].delay(unsafeCancel())

      def unsafeCancel(): Unit = {
        task.cancel()
        ()
      }

      timer.schedule(task, delay.toMillis)
    }
}

object Scheduler {

  trait JobHandler[F[_], T] {
    def unsafeCancel(): Unit
    def cancel(): F[Unit]
    def result: F[T]
  }
}
