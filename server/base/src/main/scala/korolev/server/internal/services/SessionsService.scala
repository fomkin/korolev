package korolev.server.internal.services

import korolev.Qsid
import korolev.effect.syntax._
import korolev.effect.{Effect, Reporter, Stream}
import korolev.internal.{ApplicationInstance, Frontend}
import korolev.server.KorolevServiceConfig
import korolev.server.Request.RequestHeader
import korolev.server.internal.{BadRequestException, Cookies}
import korolev.state.{StateDeserializer, StateSerializer, StateStorage}

import scala.collection.concurrent.TrieMap
import scala.util.Random

private[korolev] final class SessionsService[F[_]: Effect, S: StateSerializer: StateDeserializer, M](
    config: KorolevServiceConfig[F, S, M]) {

  type App = ApplicationInstance[F, S, M]

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
      defaultState <- config.stateLoader(qsid.deviceId, Some(rh))
      state <- config.router.toState
        .lift(rh.path)
        .fold(Effect[F].pure(defaultState))(f => f(defaultState))
      _ <- stateStorage.create(qsid.deviceId, qsid.sessionId, state)
    } yield state

  def findApp(qsid: Qsid): F[App] =
    Effect[F].delay {
      apps.get(qsid) match {
        case Some(Right(app)) => app
        case Some(Left(_))    => throw ErrorInitInProgress
        case _                => throw ErrorSessionNotFound(qsid)
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
            Effect[F].fail[App](ErrorInitInProgress)
        }
      }
    } yield {
      app
    }

  private val apps = TrieMap.empty[Qsid, Either[InitInProgress, App]]

  private def appsFactory(qsid: Qsid, rh: RequestHeader, incoming: Stream[F, String]) =
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
        config.render,
        config.router,
        config.reporter
      )
      _ <- app.initialize(reload)
    } yield {
      incoming.consumed.runAsync { _ =>
        stateStorage.remove(qsid.deviceId, qsid.sessionId)
        apps.remove(qsid)
      } // TODO use .start
      apps.put(qsid, Right(app))
      app
    }

  private implicit val reporter: Reporter =
    config.reporter

  private val stateStorage =
    if (config.stateStorage == null) new StateStorage.DefaultStateStorage[F, S]()
    else config.stateStorage

  private case class InitInProgress(request: Int)

  private def ErrorSessionNotFound(qsid: Qsid) =
    BadRequestException(s"There is no app instance matched to $qsid")

  private final val ErrorInitInProgress =
    BadRequestException("Session initialization in progress. Please try again later.")
}
