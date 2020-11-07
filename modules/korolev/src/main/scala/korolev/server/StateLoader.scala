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

package korolev.server

import korolev.effect.Effect
import korolev.state.DeviceId
import korolev.web.Request.Head

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
  def apply[F[_], S](f: (DeviceId, Head) => F[S]): StateLoader[F, S] = f
}
