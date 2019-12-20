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

package korolev

import korolev.effect.Effect
import korolev.server.Request.RequestHeader
import korolev.server.internal.services._
import korolev.server.internal.{FormDataCodec, KorolevServiceImpl}
import korolev.state.{DeviceId, StateDeserializer, StateSerializer}

package object server {

  type StateLoader[F[_], S] = (DeviceId, Option[RequestHeader]) => F[S]

  def korolevService[F[_]: Effect, S: StateSerializer: StateDeserializer, M](
      config: KorolevServiceConfig[F, S, M]): KorolevService[F] = {

    val commonService = new CommonService[F]()
    val filesService = new FilesService[F](commonService)
    val sessionsService = new SessionsService[F, S, M](config)
    val messagingService = new MessagingService[F](config.reporter, commonService, sessionsService)
    val formDataCodec = new FormDataCodec(config.maxFormDataEntrySize)
    val postService = new PostService[F](config.reporter, sessionsService, commonService, formDataCodec)
    val ssrService = new ServerSideRenderingService[F, S](sessionsService, config)

    new KorolevServiceImpl[F](
      config.reporter,
      commonService,
      filesService,
      messagingService,
      postService,
      ssrService
    )
  }

}
