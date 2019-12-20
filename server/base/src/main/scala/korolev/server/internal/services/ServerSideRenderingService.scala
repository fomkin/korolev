package korolev.server.internal.services

import korolev.{Context, Qsid, Router}
import korolev.effect.Effect
import korolev.effect.syntax._
import korolev.server.{Headers, KorolevServiceConfig, Request, Response}
import korolev.server.Response.Status
import korolev.server.internal.{Cookies, HtmlRenderContext}
import levsha.Document.Node

private[korolev] final class ServerSideRenderingService[F[_]: Effect, S](sessionsService: SessionsService[F, S, _],
                                                                         config: KorolevServiceConfig[F, S, _]) {

  def canBeRendered(path: Router.Path): Boolean =
    config.router.toState.isDefinedAt(path)

  def serverSideRenderedPage(request: Request.Http[F]): F[Response.Http[F]] = {

    def connectionLostWidgetHtml() = {
      val rc = new HtmlRenderContext[F, S]()
      config.connectionLostWidget(rc)
      rc.mkString
    }

    def page(qsid: Qsid, state: S): Node[Context.Binding[F, S, _]] = {

      val rootPath = config.rootPath
      val clw = connectionLostWidgetHtml()
      val heartbeatInterval = config.heartbeatInterval.toMillis
      val kfg = s"window['kfg']={sid:'${qsid.id}',r:'$rootPath',clw:'$clw',heartbeatInterval:$heartbeatInterval}"

      import levsha.dsl._
      import html._

      optimize {
        Html(
          head(
            script(language := "javascript", kfg),
            script(src := config.rootPath + "static/korolev-client.min.js", defer := ""),
            config.head(state)
          ),
          config.render(state)
        )
      }
    }

    for {
      qsid <- sessionsService.initSession(request)
      state <- sessionsService.initAppState(qsid, request)
    } yield {
      val rc = new HtmlRenderContext[F, S]()
      rc.builder.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">\n")
      page(qsid, state).apply(rc)
      Response.Http(
        Status.Ok,
        rc.mkString,
        Seq(
          Headers.ContentTypeHtmlUtf8,
          Headers.setCookie(Cookies.DeviceId, qsid.deviceId, config.rootPath)
        )
      )
    }
  }
}
