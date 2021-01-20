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

package korolev.internal

import korolev.effect.{Effect, Reporter}
import korolev.effect.syntax._

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

/**
  * Save information about what type of events are already
  * listening on the client
  */
final class EventRegistry[F[_]: Effect](frontend: Frontend[F])(implicit reporter: Reporter) {

  private val knownEventTypes = new AtomicReference(Set("submit"))

  /**
    * Notifies client side that he should listen
    * all events of the type. If event already listening
    * on the client side, client will be not notified again.
    */
  def registerEventType(`type`: String): Unit = {
    @tailrec
    def aux(): Unit = {
      val ref = knownEventTypes.get
      if (!ref.contains(`type`)) {
        val newValue = ref + `type`
        if (knownEventTypes.compareAndSet(ref, newValue)) {
          frontend
            .listenEvent(`type`, preventDefault = false)
            .runAsyncForget
        } else {
          aux()
        }
      }
    }
    aux()
  }
}
