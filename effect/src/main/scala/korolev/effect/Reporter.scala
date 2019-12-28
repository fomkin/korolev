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

/**
  * Korolev INTERNAL reporting subsystem.
  * Do not use it in application code.
  */
trait Reporter {
  implicit val Implicit: Reporter = this
  def error(message: String, cause: Throwable): Unit
  def error(message: String): Unit
  def warning(message: String, cause: Throwable): Unit
  def warning(message: String): Unit
  def info(message: String): Unit
}

object Reporter {

  /**
    * Default STDOUT reporting
    */
  final object PrintReporter extends Reporter {
    def error(message: String, error: Throwable): Unit = {
      print(s"[ERROR] $message")
      error.printStackTrace(System.out)
    }
    def error(message: String): Unit = {
      println(s"[ERROR] $message")
    }
    def warning(message: String, warning: Throwable): Unit = {
      print(s"[WARNING] $message")
      warning.printStackTrace(System.out)
    }
    def warning(message: String): Unit = {
      println(s"[WARNING] $message")
    }
    def info(message: String): Unit = {
      println(s"[INFO] $message")
    }
  }
}
