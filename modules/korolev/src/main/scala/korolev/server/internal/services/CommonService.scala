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

package korolev.server.internal.services

import korolev.effect.Effect
import korolev.effect.io.LazyBytes
import korolev.server.HttpResponse
import korolev.server.internal.HttpResponse
import korolev.web.Response

private[korolev] final class CommonService[F[_]: Effect] {

  val notFoundResponseF: F[HttpResponse[F]] =
    HttpResponse(Response.Status.NotFound, "Not found", Nil)

  def badRequest(message: String): F[HttpResponse[F]] =
    HttpResponse(Response.Status.BadRequest, s"Bad request. $message", Nil)

  val simpleOkResponse: HttpResponse[F] = Response(
    status = Response.Status.Ok,
    body = LazyBytes.empty[F],
    headers = Nil,
    contentLength = Some(0L)
  )

}
