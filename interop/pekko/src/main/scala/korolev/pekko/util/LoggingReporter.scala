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

package korolev.pekko.util

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.event.{LogSource, Logging}
import korolev.effect.Reporter

final class LoggingReporter(actorSystem: ActorSystem) extends Reporter {

  private implicit val logSource: LogSource[LoggingReporter] = new LogSource[LoggingReporter] {
    def genString(t: LoggingReporter): String = "korolev"
  }

  private val log = Logging(actorSystem, this)

  def error(message: String, cause: Throwable): Unit = log.error(cause, message)
  def error(message: String): Unit = log.error(message)
  def warning(message: String, cause: Throwable): Unit = log.warning(s"$message: {}", cause)
  def warning(message: String): Unit = log.warning(message)
  def info(message: String): Unit = log.info(message)
  def debug(message: String): Unit = log.debug(message)
  def debug(message: String, arg1: Any): Unit = log.debug(message, arg1)
  def debug(message: String, arg1: Any, arg2: Any): Unit = log.debug(message, arg1, arg2)
  def debug(message: String, arg1: Any, arg2: Any, arg3: Any): Unit = log.debug(message, arg1, arg2, arg3)
}
