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
import korolev.web.PathAndQuery._
import korolev.web.Request

private[korolev] final class KorolevServiceImpl[F[_]: Effect](http: PartialFunction[HttpRequest[F], F[HttpResponse[F]]],
                                                              commonService: CommonService[F],
                                                              filesService: FilesService[F],
                                                              messagingService: MessagingService[F],
                                                              postService: PostService[F],
                                                              ssrService: ServerSideRenderingService[F, _, _])
    extends KorolevService[F] {

  def http(request: HttpRequest[F]): F[HttpResponse[F]] = {
    (request.cookie(Cookies.DeviceId), request.pq) match {

      // Static files
      case _ -> Root / "static" =>
        commonService.notFoundResponseF
      case _ -> path if path.startsWith("static") =>
        filesService.resourceFromClasspath(path)

      // Long polling
      case Some(deviceId) -> Root / "bridge" / "long-polling" / sessionId / "publish" =>
        messagingService.longPollingPublish(Qsid(deviceId, sessionId), request.body)
      case Some(deviceId) -> Root / "bridge" / "long-polling" / sessionId / "subscribe" =>
        messagingService.longPollingSubscribe(Qsid(deviceId, sessionId), request)

      // Data for app given via POST requests
      case Some(deviceId) -> Root / "bridge" / sessionId / "form-data" / descriptor =>
        postService.formData(Qsid(deviceId, sessionId), descriptor, request.headers, request.body)
      case Some(deviceId) -> Root / "bridge" / sessionId / "file" / descriptor / "info" =>
        postService.filesInfo(Qsid(deviceId, sessionId), descriptor, request.body)
      case Some(deviceId) -> Root / "bridge" / sessionId / "file" / descriptor / _ =>
        postService.downloadFile(Qsid(deviceId, sessionId), descriptor)
      case Some(deviceId) -> Root / "bridge" / sessionId / "file" / descriptor =>
        postService.uploadFile(Qsid(deviceId, sessionId), descriptor, request.headers, request.body)

      // Server side rendering
      case _ -> path if path == Root || ssrService.canBeRendered(request.pq) =>
        ssrService.serverSideRenderedPage(request)

      // Not found
      case _ => http.applyOrElse(request, (_: HttpRequest[F]) => commonService.notFoundResponseF)
    }

  }

  def ws(request: WebSocketRequest[F]): F[WebSocketResponse[F]] = {
    (request.cookie(Cookies.DeviceId), request.pq) match {
      case (Some(deviceId), Root / "bridge" / "web-socket" / sessionId) =>
        messagingService.webSocketMessaging(Qsid(deviceId, sessionId), request, request.body)
      case _ =>
        webSocketBadRequestF
    }
  }

  private val webSocketBadRequestF = {
    val error = BadRequestException("Malformed request. Headers MUST contain deviceId cookie. Path MUST be '/bridge/web-socket/<session>'.")
    Effect[F].fail[WebSocketResponse[F]](error)
  }
}
