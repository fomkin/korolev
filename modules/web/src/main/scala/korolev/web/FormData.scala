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

package korolev.web

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import korolev.web.FormData.Entry

final case class FormData(content: Seq[Entry]) {

  def text(name: String): String = {
    val array = bytes(name).array()
    new String(array, StandardCharsets.UTF_8)
  }

  def bytes(name: String): ByteBuffer = bytesOpt(name).get

  def bytesOpt(name: String): Option[ByteBuffer] = {
    apply(name).map(_.value)
  }

  def contentType(name: String): Option[String] = {
    apply(name) flatMap { entry =>
      entry.headers collectFirst {
        case (k, v) if k.toLowerCase == "content-type" => v
      }
    }
  }

  def apply(name: String): Option[Entry] =
    content.find(_.name == name)
}

object FormData {

  case class Entry(name: String, value: ByteBuffer, headers: Seq[(String, String)]) {
    lazy val asString: String = new String(value.array(), StandardCharsets.UTF_8)
  }
}
