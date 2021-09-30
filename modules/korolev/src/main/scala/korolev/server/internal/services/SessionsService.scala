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

import korolev.effect.syntax._
import korolev.effect.{AsyncTable, Effect, Scheduler, Stream}
import korolev.internal.{ApplicationInstance, Frontend}
import korolev.server.KorolevServiceConfig
import korolev.server.internal.{BadRequestException, Cookies}
import korolev.state.{StateDeserializer, StateSerializer, StateStorage}
import korolev.web.Request.Head
import korolev.{Extension, Qsid}

import java.util.concurrent.atomic.AtomicBoolean

private[korolev] final class SessionsService[F[_]: Effect, S: StateSerializer: StateDeserializer, M](
    config: KorolevServiceConfig[F, S, M],
    pageService: PageService[F, S, M]) {

  import config.executionContext
  import config.reporter.Implicit

  type App = ApplicationInstance[F, S, M]
  type ExtensionsHandlers = List[Extension.Handlers[F, S, M]]

  private val apps = AsyncTable.unsafeCreateEmpty[F, Qsid, App]

  def initSession(rh: Head): F[Qsid] =
    for {
      deviceId <- rh.cookie(Cookies.DeviceId) match {
        case Some(d) => Effect[F].pure(d)
        case None => config.idGenerator.generateDeviceId()
      }
      sessionId <- config.idGenerator.generateSessionId()
    } yield {
      Qsid(deviceId, sessionId)
    }

  def initAppState(qsid: Qsid, rh: Head): F[S] =
    for {
      defaultState <- config.stateLoader(qsid.deviceId, rh)
      state <- config.router.toState
        .lift(rh.pq)
        .fold(Effect[F].pure(defaultState))(f => f(defaultState))
      _ <- stateStorage.create(qsid.deviceId, qsid.sessionId, state)
    } yield state

  def getApp(qsid: Qsid): F[Option[App]] =
    apps.getImmediately(qsid)

  def createAppIfNeeded(qsid: Qsid, rh: Head, incomingStream: Stream[F, String]): F[Unit] = {

    val (incomingConsumed, incoming) = incomingStream.handleConsumed

    def handleAppOrWsOutgoingClose(frontend: Frontend[F], app: App, ehs: ExtensionsHandlers): F[Unit] = {
      val consumed: F[Unit] = Effect[F].promise[Unit] { cb =>
        val consumed = new AtomicBoolean(false)
        frontend.outgoingConsumed.runAsync(f =>
          if(consumed.compareAndSet(false, true)) {
            cb(f)
          }
        )
        incomingConsumed.runAsync(f =>
          if(consumed.compareAndSet(false, true)) {
            cb(f)
          }
        )
      }

      for {
        _ <- consumed
        _ <- incoming.cancel()
        _ <- frontend.outgoingMessages.cancel()
        _ <- app.topLevelComponentInstance.destroy()
        _ <- ehs.map(_.onDestroy()).sequence
        _ <- Effect[F].delay(stateStorage.remove(qsid.deviceId, qsid.sessionId))
        _ <- apps.remove(qsid)
      } yield ()
    }

    def handleStateChange(app: App, ehs: ExtensionsHandlers): F[Unit] =
      app.stateStream.foreach { case (id, state) =>
        if (id != levsha.Id.TopLevel) Effect[F].unit else ehs
          .map(_.onState(state.asInstanceOf[S]))
          .sequence
          .unit
      }

    def handleMessages(app: App, ehs: ExtensionsHandlers): F[Unit] =
        app.messagesStream.foreach { m =>
          ehs.map(_.onMessage(m)).sequence.unit
        }

    def create() =
      for {
        stateManager <- stateStorage.get(qsid.deviceId, qsid.sessionId)
        maybeInitialState <- stateManager.read[S](levsha.Id.TopLevel)
        // Top level state should exists. See 'initAppState'.
        initialState <- maybeInitialState.fold(Effect[F].fail[S](BadRequestException(s"Top level state should exists. Snapshot for $qsid is corrupted")))(Effect[F].pure(_))
        frontend = new Frontend[F](incoming)
        app = new ApplicationInstance[F, S, M](
          qsid,
          frontend,
          stateManager,
          initialState,
          config.document,
          config.router,
          createMiscProxy = (rc, k) => pageService.setupStatefulProxy(rc, qsid, k),
          scheduler,
          config.reporter
        )
        browserAccess = app.topLevelComponentInstance.browserAccess
        _ <- config.extensions.map(_.setup(browserAccess))
          .sequence
          .flatMap { ehs =>
            for {
              _ <- Effect[F].start(handleStateChange(app, ehs))
              _ <- Effect[F].start(handleMessages(app, ehs))
              _ <- Effect[F].start(handleAppOrWsOutgoingClose(frontend, app, ehs))
            } yield ()
          }
          .start
        _ <- app.initialize()
      } yield {
        app
      }

    stateStorage.exists(qsid.deviceId, qsid.sessionId) flatMap {
      case true =>
        // State exists because it was created on static page
        // rendering phase (see ServerSideRenderingService)
        apps
          .getFill(qsid)(create())
          .unit
      case false =>
        // State is not exists. Do nothing
        apps.remove(qsid)
    }
  }

  private val stateStorage =
    if (config.stateStorage == null) StateStorage[F, S]()
    else config.stateStorage

  private val scheduler = new Scheduler[F]()
}
