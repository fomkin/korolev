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
import korolev.server.Response

private[korolev] final class CommonService[F[_]: Effect] {

  val notFoundResponse: Response.Http[F] =
    Response.Http(Response.Status.NotFound, "Not found", Nil)

  val notFoundResponseF: F[Response.Http[F]] =
    Effect[F].pure(notFoundResponse)

  val simpleOkResponse: Response.Http[F] = Response(
    status = Response.Status.Ok,
    body = LazyBytes.empty[F],
    headers = Nil
  )

}
