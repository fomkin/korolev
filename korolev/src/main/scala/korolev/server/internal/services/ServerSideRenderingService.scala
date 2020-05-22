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
      val kfg = s"window['kfg']={sid:'${qsid.sessionId}',r:'$rootPath',clw:'$clw',heartbeatInterval:$heartbeatInterval}"

      import levsha.dsl._
      import html._

      optimize {
        Html(
          head(
            script(kfg),
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
      rc.builder.append("<!DOCTYPE html>\n")
      page(qsid, state).apply(rc)
      Response.Http(
        Status.Ok,
        rc.mkString,
        Seq(
          Headers.ContentTypeHtmlUtf8,
          Headers.CacheControlNoCache,
          Headers.setCookie(Cookies.DeviceId, qsid.deviceId, config.rootPath,
            maxAge = 60 * 60 * 24 * 365 * 10 /* 10 years */)
        )
      )
    }
  }
}
