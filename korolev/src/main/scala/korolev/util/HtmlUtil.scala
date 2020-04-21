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

package korolev.util

object HtmlUtil {

  def camelCaseToSnakeCase(value: String, startIndex: Int): String = {
    val sb = new StringBuilder()
    camelCaseToSnakeCase(sb, value, startIndex)
    sb.mkString
  }

  def camelCaseToSnakeCase(value: String, prefix: Char, startIndex: Int): String = {
    val sb = new StringBuilder()
    sb.append(prefix)
    camelCaseToSnakeCase(sb, value, startIndex)
    sb.mkString
  }

  def camelCaseToSnakeCase(sb: StringBuilder, value: String, startIndex: Int): Unit = {
    var i = startIndex
    while (i < value.length) {
      val char = value(i)
      if (Character.isUpperCase(char)) {
        sb.append('-')
        sb.append(Character.toLowerCase(char))
      } else {
        sb.append(char)
      }
      i += 1
    }
  }
}
