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

import java.util.concurrent.Executors

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

package object execution {

  private val schedulerCache = TrieMap.empty[Any, Any]

  implicit val defaultExecutor: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newWorkStealingPool())

  implicit def defaultScheduler[F[+_]: Async]: Scheduler[F] = {
    val async = Async[F]
    val scheduler = schedulerCache.getOrElseUpdate(async, new JavaTimerScheduler[F])
    scheduler.asInstanceOf[Scheduler[F]]
  }
}
