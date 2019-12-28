package korolev.server.internal

import korolev.effect.{Effect, Reporter}
import korolev.server.internal.services._
import korolev.server.{KorolevService, Request, Response}
import korolev.{/, Qsid, Root}

private[korolev] final class KorolevServiceImpl[F[_]: Effect](reporter: Reporter,
                                                              commonService: CommonService[F],
                                                              filesService: FilesService[F],
                                                              messagingService: MessagingService[F],
                                                              postService: PostService[F],
                                                              ssrService: ServerSideRenderingService[F, _])
    extends KorolevService[F] {

  def http(request: Request.Http[F]): F[Response.Http[F]] = {
    request match {

      // Static files
      case Request(Root / "static", _, _, _, _) =>
        commonService.notFoundResponseF
      case Request(path, _, _, _, _) if path.startsWith("static") =>
        filesService.resourceFromClasspath(path)

      // Long polling
      case Request(Root / "bridge" / "long-polling" / deviceId / sessionId / "publish", _, _, _, body) =>
        messagingService.longPollingPublish(Qsid(deviceId, sessionId), body)
      case r @ Request(Root / "bridge" / "long-polling" / deviceId / sessionId / "subscribe", _, _, _, _) =>
        messagingService.longPollingSubscribe(Qsid(deviceId, sessionId), r)

      // Data for app given via POST requests
      case Request(Root / "bridge" / deviceId / sessionId / "form-data" / descriptor, _, _, headers, body) =>
        postService.formData(Qsid(deviceId, sessionId), descriptor, headers, body)
      case Request(Root / "bridge" / deviceId / sessionId / "file" / descriptor / "info", _, _, _, body) =>
        postService.filesInfo(Qsid(deviceId, sessionId), descriptor, body)
      case Request(Root / "bridge" / deviceId / sessionId / "file" / descriptor, _, _, headers, body) =>
        postService.file(Qsid(deviceId, sessionId), descriptor, headers, body)

      // Server side rendering
      case request if request.path == Root || ssrService.canBeRendered(request.path) =>
        ssrService.serverSideRenderedPage(request)

      // Not found
      case _ =>
        commonService.notFoundResponseF
    }

  }

  def ws(request: Request.WebSocket[F]): F[Response.WebSocket[F]] = {
    request match {
      case r @ Request(Root / "bridge" / "web-socket" / deviceId / sessionId, _, _, _, body) =>
        messagingService.webSocketMessaging(Qsid(deviceId, sessionId), r, body)
      case _ =>
        webSocketBadRequestF
    }
  }

  private val webSocketBadRequestF = {
    val error = BadRequestException("Wrong path. Should be '/bridge/web-socket/<device>/<session>'.")
    Effect[F].fail[Response.WebSocket[F]](error)
  }
}
