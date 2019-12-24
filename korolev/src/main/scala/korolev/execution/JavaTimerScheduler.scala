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

import java.util.{Timer, TimerTask}

import korolev.effect.syntax._
import korolev.effect.{Effect, Reporter}

import scala.concurrent.duration.FiniteDuration

final class JavaTimerScheduler[F[_]: Effect] extends Scheduler[F] {

  import Scheduler._

  private val timer = new Timer()

  def scheduleOnce[T](delay: FiniteDuration)(job: => T)(implicit r: Reporter): JobHandler[F, T] = {
    val promise = Effect[F].strictPromise[T]
    val task = new TimerTask {
      def run(): Unit = {
        val result = Effect[F]
          .unit
          .fork()
          .map(_ => job)
        promise.completeAsync(result)
      }
    }
    timer.schedule(task, delay.toMillis)
    JobHandler(
      cancel = () => { task.cancel(); () },
      result = promise.effect
    )
  }

  def schedule[U](interval: FiniteDuration)(job: => U)(implicit r: Reporter): Cancel = {
    val task = new TimerTask {
      def run(): Unit =
        Effect[F]
          .unit
          .fork()
          .map(_ => job)
          .runAsyncForget
    }
    val millis = interval.toMillis
    timer.schedule(task, millis, millis)
    () => { task.cancel(); () }
  }
}
