package korolev.server

import korolev.effect.Effect
import korolev.server.Request.RequestHeader
import korolev.state.DeviceId

object StateLoader {

  /**
   * State is same for all sessions.
   *
   * @param initialState State factory
   * @tparam S Type of state
   */
  def default[F[_] : Effect, S](initialState: S): StateLoader[F, S] = {
    val value = Effect[F].pure(initialState)
    (_, _) => value // always return same object
  }

  /**
   * State depends on deviceId. Useful when you want to
   * restore user authorization.
   *
   * {{{
   * case class MyState(deviceId: DeviceId, ...)
   *
   * StateLoader.forDeviceId { deviceId =>
   *   MyStorage.getStateByDeviceId(deviceId) map {
   *     case Some(state) => state
   *     case None => MyState(deviceId, ...)
   *   }
   * }
   * }}}
   */
  def forDeviceId[F[_], S](initialState: DeviceId => F[S]): StateLoader[F, S] =
    (deviceId, _) => initialState(deviceId)

  /**
   * State depends on deviceId and HTTP-request. Second one
   * could be None if case when user reconnected to
   * restarted application and state wasn't restored.
   */
  def apply[F[_], S](f: (DeviceId, Option[RequestHeader]) => F[S]): StateLoader[F, S] = f
}
