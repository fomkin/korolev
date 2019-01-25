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

import korolev.{Async, Reporter}
import korolev.Async.AsyncOps

import scala.concurrent.duration.FiniteDuration
import scala.util.Success

final class JavaTimerScheduler[F[_]: Async] extends Scheduler[F] {

  import Scheduler._

  private val timer = new Timer()
  private val async = Async[F]

  def scheduleOnce[T](delay: FiniteDuration)(job: => T)(implicit r: Reporter): JobHandler[F, T] = {
    val promise = Async[F].promise[T]
    val task = new TimerTask {
      def run(): Unit = {
        val task = async.fork {
          val result = job // Execute a job
          promise.complete(Success(result))
        }
        task.runIgnoreResult
      }
    }
    timer.schedule(task, delay.toMillis)
    JobHandler(
      cancel = () => { task.cancel(); () },
      result = promise.future
    )
  }

  def schedule[U](interval: FiniteDuration)(job: => U)(implicit r: Reporter): Cancel = {
    val task = new TimerTask {
      def run(): Unit = async.fork(job).runIgnoreResult
    }
    val millis = interval.toMillis
    timer.schedule(task, millis, millis)
    () => { task.cancel(); () }
  }
}
