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

package korolev.blazeServer

import korolev.Async
import korolev.execution.Scheduler
import korolev.server.{KorolevServiceConfig, MimeTypes}
import korolev.state.{StateDeserializer, StateSerializer}
import org.http4s.blaze.http.HttpService

final class BlazeServiceBuilder[F[+_]: Async: Scheduler, S: StateSerializer: StateDeserializer, M](mimeTypes: MimeTypes) {
  def from(config: KorolevServiceConfig[F, S, M]): HttpService =
    blazeService(config, mimeTypes)
}
