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

import monix.eval.Task
import monix.execution.Scheduler

import scala.collection.mutable
import scala.concurrent.ExecutionContext

package object monixSupport {

  private val ecTaskInstanceCache =
    mutable.Map.empty[Scheduler, Async[Task]]

  private val schedulerTaskInstanceCache =
    mutable.Map.empty[ExecutionContext, Async[Task]]

  /**
    * Creates an Async instance for Monix Task.
    * Physically one instance per scheduler is maintained.
    */
  implicit def schedulerTaskAsync(implicit scheduler: Scheduler): Async[Task] =
    ecTaskInstanceCache.synchronized {
      ecTaskInstanceCache.getOrElseUpdate(scheduler, new TaskAsync()(scheduler))
    }

  /**
    * Creates an Async instance for Monix Task.
    * Physically one instance per execution context is maintained.
    */
  implicit def executionContextTaskAsync(implicit ec: ExecutionContext): Async[Task] =
    schedulerTaskInstanceCache.synchronized {
      schedulerTaskInstanceCache.getOrElseUpdate(ec, new TaskAsync()(Scheduler(ec)))
    }

}
