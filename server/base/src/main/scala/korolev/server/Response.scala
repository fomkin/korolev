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
import korolev.server.Response.Status
import korolev.effect.Stream

final case class Response[Body](
    status: Status,
    body: Body,
    headers: Seq[(String, String)]
)

object Response {

  type Http[F[_]] = Response[LazyBytes[F]]
  type WebSocket[F[_]] = Response[Stream[F, String]]

  def Http[F[_]: Effect](status: Status): Response.Http[F] = {
    new Response(status, LazyBytes.empty[F], Nil)
  }

  def Http[F[_]: Effect](status: Status, body: Array[Byte], headers: Seq[(String, String)]): Response.Http[F] = {
    new Response(status, LazyBytes[F](body), headers)
  }

  def Http[F[_]: Effect](status: Status, message: String, headers: Seq[(String, String)]): Response.Http[F] = {
    Response.Http(status, message.getBytes(StandardCharsets.UTF_8), headers)
  }

  final case class Status(code: Int, phrase: String) {
    val codeAsString: String = code.toString
  }

  object Status {
    val Ok: Status = Status(200, "OK")
    val NotFound: Status = Status(404, "Not Found")
    val BadRequest: Status = Status(400, "Bad Request")
    val Gone: Status = Status(410, "Gone")
  }
}
