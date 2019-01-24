/*
 * Copyright 2017-2018 Aleksey Fomkin
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

package korolev.state

import java.security.SecureRandom

import korolev.Async

trait IdGenerator[F[_]] {
  def generateDeviceId()(implicit F: Async[F]): F[DeviceId]
  def generateSessionId()(implicit F: Async[F]): F[SessionId]
}

object IdGenerator {

  val DefaultDeviceIdLength = 64
  val DefaultSessionIdLength = 64

  def default[F[_]](deviceIdLength: Int = DefaultDeviceIdLength,
                     sessionIdLength: Int = DefaultSessionIdLength): IdGenerator[F] =
    new DefaultIdGenerator[F](DefaultDeviceIdLength, DefaultSessionIdLength)

  private class DefaultIdGenerator[F[_]](deviceIdLength: Int,
                                          sessionIdLength: Int) extends IdGenerator[F] {
    def generateDeviceId()(implicit F: Async[F]): F[DeviceId] =
      Async[F].pure {
        secureRandomString(deviceIdLength)
      }

    def generateSessionId()(implicit F: Async[F]): F[SessionId] =
      Async[F].pure {
        secureRandomString(sessionIdLength)
      }

    private val Alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private val rnd = new SecureRandom

    private def secureRandomString(len: Int): String = {
      val sb = new StringBuilder(len)
      var i = 0
      while (i < len) {
        sb.append(Alphabet.charAt(rnd.nextInt(Alphabet.length)))
        i += 1
      }
      sb.toString
    }
  }

}
