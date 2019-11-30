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

package korolev.server.internal

import korolev.Qsid
import korolev.effect.Effect
import korolev.server.internal.services._
import korolev.server._
import korolev.web.Path._

private[korolev] final class KorolevServiceImpl[F[_]: Effect](http: PartialFunction[HttpRequest[F], F[HttpResponse[F]]],
                                                              commonService: CommonService[F],
                                                              filesService: FilesService[F],
                                                              messagingService: MessagingService[F],
                                                              postService: PostService[F],
                                                              ssrService: ServerSideRenderingService[F, _, _])
    extends KorolevService[F] {

  def http(request: HttpRequest[F]): F[HttpResponse[F]] = {
    request.path match {

      // Static files
      case Root / "static" =>
        commonService.notFoundResponseF
      case path if path.startsWith("static") =>
        filesService.resourceFromClasspath(path)

      // Long polling
      case Root / "bridge" / "long-polling" / deviceId / sessionId / "publish" =>
        messagingService.longPollingPublish(Qsid(deviceId, sessionId), request.body)
      case Root / "bridge" / "long-polling" / deviceId / sessionId / "subscribe" =>
        messagingService.longPollingSubscribe(Qsid(deviceId, sessionId), request)

      // Data for app given via POST requests
      case Root / "bridge" / deviceId / sessionId / "form-data" / descriptor =>
        postService.formData(Qsid(deviceId, sessionId), descriptor, request.headers, request.body)
      case Root / "bridge" / deviceId / sessionId / "file" / descriptor / "info" =>
        postService.filesInfo(Qsid(deviceId, sessionId), descriptor, request.body)
      case Root / "bridge" / deviceId / sessionId / "file" / descriptor =>
        postService.file(Qsid(deviceId, sessionId), descriptor, request.headers, request.body)

      // Server side rendering
      case path if path == Root || ssrService.canBeRendered(request.path) =>
        ssrService.serverSideRenderedPage(request)

      // Not found
      case _ => http.applyOrElse(request, (_: HttpRequest[F]) => commonService.notFoundResponseF)
    }

  }

  def ws(request: WebSocketRequest[F]): F[WebSocketResponse[F]] = {
    request.path match {
      case Root / "bridge" / "web-socket" / deviceId / sessionId =>
        messagingService.webSocketMessaging(Qsid(deviceId, sessionId), request, request.body)
      case _ =>
        webSocketBadRequestF
    }
  }

  private val webSocketBadRequestF = {
    val error = BadRequestException("Wrong path. Should be '/bridge/web-socket/<device>/<session>'.")
    Effect[F].fail[WebSocketResponse[F]](error)
  }
}
