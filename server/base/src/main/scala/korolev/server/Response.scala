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

import korolev.LazyBytes
import korolev.effect.Effect

sealed trait Response[F[_]]

object Response {

  case class Http[F[_]](status: Status, body: LazyBytes[F], headers: Seq[(String, String)]) extends Response[F]

  object Http {

    def apply[F[_]: Effect](status: Status): Http[F] = {
      new Http(status, LazyBytes.empty[F], Nil)
    }

    def apply[F[_]: Effect](status: Status,
                            body: Array[Byte],
                            headers: Seq[(String, String)]): Http[F] = {
      new Http(status, LazyBytes[F](body), headers)
    }

    def apply[F[_]: Effect](status: Status,
                            message: String,
                            headers: Seq[(String, String)]): Http[F] = {
      Http[F](status, message.getBytes(StandardCharsets.UTF_8), headers)
    }
  }

  case class WebSocket[F[_]](publish: String => Unit, subscribe: (String => Unit) => Unit, destroyHandler: () => Unit)
      extends Response[F]

  case class Status(code: Int, phrase: String) {
    val codeAsString: String = code.toString
  }

  object Status {
    val Ok = Status(200, "OK")
    val NotFound = Status(404, "Not Found")
    val BadRequest = Status(400, "Bad Request")
    val Gone = Status(410, "Gone")
  }
}
