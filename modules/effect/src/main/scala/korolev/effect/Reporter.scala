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
  def debug(message: String): Unit
  def debug(message: String, arg1: Any): Unit
  def debug(message: String, arg1: Any, arg2: Any): Unit
  def debug(message: String, arg1: Any, arg2: Any, arg3: Any): Unit
//  def debug(message: String, args: Any*): Unit
}

object Reporter {

  object Level extends Enumeration {
    final val Debug = Value(0, "Debug")
    final val Info = Value(1, "Info")
    final val Warning = Value(2, "Warning")
    final val Error = Value(3, "Error")
  }

  final object PrintReporter extends Reporter {

    var level: Level.Value = Level.Info

    def error(message: String, error: Throwable): Unit = if (level <= Level.Error) {
      println(s"[ERROR] $message")
      error.printStackTrace(System.out)
    }
    def error(message: String): Unit = if (level <= Level.Error) {
      println(s"[ERROR] $message")
    }
    def warning(message: String, error: Throwable): Unit = if (level <= Level.Warning) {
      println(s"[WARNING] $message")
      error.printStackTrace(System.out)
    }
    def warning(message: String): Unit = if (level <= Level.Warning) {
      println(s"[WARNING] $message")
    }
    def info(message: String): Unit = if (level <= Level.Info) {
      println(s"[INFO] $message")
    }
    def debug(message: String): Unit = if (level <= Level.Debug) {
      println(s"[DEBUG] $message")
    }
    def debug(message: String, arg1: Any): Unit = if (level <= Level.Debug) {
      println(s"[DEBUG] ${message.format(arg1)}")
    }
    def debug(message: String, arg1: Any, arg2: Any): Unit = if (level <= Level.Debug) {
      println(s"[DEBUG] ${message.format(arg1, arg2)}")
    }
    def debug(message: String, arg1: Any, arg2: Any, arg3: Any): Unit = if (level <= Level.Debug) {
      println(s"[DEBUG] ${message.format(arg1, arg2, arg3)}")
    }
//    def debug(message: String, args: Any*): Unit = if (level <= Level.Debug) {
//      println(s"[DEBUG] ${String.format(message, args:_*)}")
//    }
  }
}
