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

package korolev.state

import korolev.effect.Effect
import levsha.Id

abstract class StateManager[F[_]: Effect] { self =>
  def snapshot: F[StateManager.Snapshot]
  def read[T: StateDeserializer](nodeId: Id): F[Option[T]]
  def delete(nodeId: Id): F[Unit]
  def write[T: StateSerializer](nodeId: Id, value: T): F[Unit]
}

object StateManager {

  trait Snapshot {
    def apply[T: StateDeserializer](nodeId: Id): Option[T]
  }
}
