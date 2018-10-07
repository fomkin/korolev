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

package korolev.server

import java.nio.charset.StandardCharsets

sealed trait Response

object Response {

  case class Http(status: Status,
                  body: Option[Array[Byte]] = None,
                  headers: Seq[(String, String)] = Seq.empty)
      extends Response

  object Http {
    def apply(status: Status, message: String): Http = {
      val bytes = message.getBytes(StandardCharsets.UTF_8)
      Http(status, Some(bytes))
    }
  }

  case class WebSocket(publish: String => Unit,
                       subscribe: (String => Unit) => Unit,
                       destroyHandler: () => Unit)
      extends Response

  sealed trait Status {
    def code: Int
    def phrase: String
  }

  object Status {
    case object Ok extends Status {
      val code = 200
      val phrase = "OK"
    }
    case object BadRequest extends Status {
      val code = 400
      val phrase = "Bad Request"
    }
    case object Gone extends Status {
      val code = 410
      val phrase = "Gone"
    }
  }
}
