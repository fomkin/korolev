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

import korolev.{Extension, Qsid}
import korolev.effect.syntax._
import korolev.effect.{Effect, Scheduler, Stream}
import korolev.internal.{ApplicationInstance, Frontend}
import korolev.server.KorolevServiceConfig
import korolev.server.Request.RequestHeader
import korolev.server.internal.{BadRequestException, Cookies}
import korolev.state.{StateDeserializer, StateSerializer, StateStorage}

import scala.collection.concurrent.TrieMap
import scala.util.Random

private[korolev] final class SessionsService[F[_]: Effect, S: StateSerializer: StateDeserializer, M](
    config: KorolevServiceConfig[F, S, M],
    pageService: PageService[F, S, M]) {

  import config.executionContext
  import config.reporter.Implicit

  type App = ApplicationInstance[F, S, M]
  type ExtensionsHandlers = List[Extension.Handlers[F, S, M]]

  def initSession(rh: RequestHeader): F[Qsid] =
    for {
      deviceId <- rh.cookie(Cookies.DeviceId) match {
        case Some(d) => Effect[F].pure(d)
        case None => config.idGenerator.generateDeviceId()
      }
      sessionId <- config.idGenerator.generateSessionId()
    } yield {
      Qsid(deviceId, sessionId)
    }

  def initAppState(qsid: Qsid, rh: RequestHeader): F[S] =
    for {
      defaultState <- config.stateLoader(qsid.deviceId, rh)
      state <- config.router.toState
        .lift(rh.path)
        .fold(Effect[F].pure(defaultState))(f => f(defaultState))
      _ <- stateStorage.create(qsid.deviceId, qsid.sessionId, state)
    } yield state

  def findApp(qsid: Qsid): F[App] =
    Effect[F].delay {
      apps.get(qsid) match {
        case Some(Right(app)) => app
        case Some(Left(_))    => throw BadRequestException(ErrorInitInProgress)
        case _                => throw BadRequestException(ErrorSessionNotFound(qsid))
      }
    }

  def findAppOrCreate(qsid: Qsid, rh: RequestHeader, incomingMessages: => Stream[F, String]): F[App] =
    for {
      requestId <- Effect[F].delay(Random.nextInt())
      inProgressOrApp <- Effect[F].delay(apps.getOrElseUpdate(qsid, Left(InitInProgress(requestId))))
      app <- {
        inProgressOrApp match {
          case Left(InitInProgress(`requestId`)) =>
            appsFactory(qsid, rh, incomingMessages)
          case Right(app) =>
            Effect[F].pure(app)
          case _ =>
            Effect[F].fail[App](BadRequestException(ErrorInitInProgress))
        }
      }
    } yield {
      app
    }

  private val apps = TrieMap.empty[Qsid, Either[InitInProgress, App]]

  private def appsFactory(qsid: Qsid, rh: RequestHeader, incomingStream: Stream[F, String]) = {

    val (incomingConsumed, incoming) = incomingStream.handleConsumed

    def handleAppClose(frontend: Frontend[F], app: App, ehs: ExtensionsHandlers) =
      for {
        _ <- incomingConsumed
        _ <- frontend.outgoingMessages.cancel()
        _ <- app.topLevelComponentInstance.destroy()
        _ <- ehs.map(_.onDestroy()).sequence
        _ <- Effect[F].delay {
          stateStorage.remove(qsid.deviceId, qsid.sessionId)
          apps.remove(qsid)
        }
      } yield ()

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

    for {
      exists <- stateStorage.exists(qsid.deviceId, qsid.sessionId)
      stateManager <-
        if (exists) stateStorage.get(qsid.deviceId, qsid.sessionId)
        else initAppState(qsid, rh).flatMap(stateStorage.create(qsid.deviceId, qsid.sessionId, _))
      maybeInitialState <- stateManager.read[S](levsha.Id.TopLevel)
      // Top level state should exists. See 'initAppState'.
      initialState = maybeInitialState.get
      reload = !exists
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
      ehs <- config.extensions.map(_.setup(browserAccess)).sequence
      _ <- Effect[F].start(handleStateChange(app, ehs))
      _ <- Effect[F].start(handleMessages(app, ehs))
      _ <- Effect[F].start(handleAppClose(frontend, app, ehs))
      _ <- app.initialize(reload)
    } yield {
      apps.put(qsid, Right(app))
      app
    }
  }

  private val stateStorage =
    if (config.stateStorage == null) StateStorage[F, S]()
    else config.stateStorage

  private val scheduler = new Scheduler[F]()

  private case class InitInProgress(request: Int)

  private def ErrorSessionNotFound(qsid: Qsid) =
    s"There is no app instance matched to $qsid"

  private final val ErrorInitInProgress =
    "Session initialization in progress. Please try again later."
}
