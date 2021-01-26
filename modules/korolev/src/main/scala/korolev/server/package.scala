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

package korolev

import korolev.Context.Binding
import korolev.data.Bytes
import korolev.effect.{Effect, Stream}
import korolev.server.internal.services._
import korolev.server.internal.{FormDataCodec, KorolevServiceImpl}
import korolev.state.{DeviceId, StateDeserializer, StateSerializer}
import korolev.web.Request.Head
import korolev.web.{Request, Response}

package object server {

  type HttpRequest[F[_]] = Request[Stream[F, Bytes]]
  type HttpResponse[F[_]] = Response[Stream[F, Bytes]]
  type WebSocketRequest[F[_]] = Request[Stream[F, String]]
  type WebSocketResponse[F[_]] = Response[Stream[F, String]]
  type StateLoader[F[_], S] = (DeviceId, Head) => F[S]

  def korolevService[F[_]: Effect, S: StateSerializer: StateDeserializer, M](
      config: KorolevServiceConfig[F, S, M]): KorolevService[F] = {

    // TODO remove this when render/node fields will be removed
    val actualConfig =
      if (config.document == null) {
        config.copy(document = { (state: S) =>
          import levsha.dsl._
          import html._
          optimize[Binding[F, S, M]] {
            Html(
              head(config.head(state)),
              config.render(state)
            )
          }
        })(implicitly[Effect[F]], config.executionContext)
      } else {
        config
      }

    import config.executionContext

    val commonService = new CommonService[F]()
    val filesService = new FilesService[F](commonService)
    val pageService = new PageService[F, S, M](actualConfig)
    val sessionsService = new SessionsService[F, S, M](actualConfig, pageService)
    val messagingService = new MessagingService[F](actualConfig.reporter, commonService, sessionsService)
    val formDataCodec = new FormDataCodec(actualConfig.maxFormDataEntrySize)
    val postService = new PostService[F](actualConfig.reporter, sessionsService, commonService, formDataCodec)
    val ssrService = new ServerSideRenderingService[F, S, M](sessionsService, pageService, actualConfig)

    new KorolevServiceImpl[F](
      config.http,
      commonService,
      filesService,
      messagingService,
      postService,
      ssrService
    )
  }

}
