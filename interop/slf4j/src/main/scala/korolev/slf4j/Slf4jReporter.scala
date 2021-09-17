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

package korolev.slf4j

import korolev.effect.Reporter
import org.slf4j.LoggerFactory

object Slf4jReporter extends Reporter {

  // All messages will be emited from one source.
  // It's unnecessary to show Korolev internals to enduser.
  private val logger = LoggerFactory.getLogger("Korolev")

  def error(message: String, cause: Throwable): Unit = logger.error(message, cause)
  def error(message: String): Unit = logger.error(message)
  def warning(message: String, cause: Throwable): Unit = logger.warn(message, cause)
  def warning(message: String): Unit = logger.warn(message)
  def info(message: String): Unit = logger.info(message)

  def debug(message: String): Unit = logger.debug(message)
  def debug(message: String, arg1: Any): Unit = logger.debug(message, arg1)
  def debug(message: String, arg1: Any, arg2: Any): Unit = logger.debug(message, arg1, arg2)
  def debug(message: String, arg1: Any, arg2: Any, arg3: Any): Unit = logger.debug(message, arg1, arg2, arg3)
}
