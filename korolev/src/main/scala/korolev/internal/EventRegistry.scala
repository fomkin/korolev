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

package korolev.internal

import korolev.effect.Effect
import korolev.effect.syntax._

import scala.collection.mutable

/**
  * Save information about what type of events are already
  * listening on the client
  */
final class EventRegistry[F[_]: Effect](frontend: Frontend[F]) {

  private val knownEventTypes = mutable.Set("submit")

  /**
    * Notifies client side that he should listen
    * all events of the type. If event already listening
    * on the client side, client will be not notified again.
    */
  def registerEventType(`type`: String): F[Unit] = knownEventTypes.synchronized {
    if (knownEventTypes.contains(`type`)) Effect[F].unit else {
      for {
        _ <- Effect[F].delay(knownEventTypes += `type`)
        _ <- frontend.listenEvent(`type`, preventDefault = false)
      } yield ()
    }
  }
}
