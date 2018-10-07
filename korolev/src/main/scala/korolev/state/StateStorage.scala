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

import java.io.{File, FileInputStream, FileOutputStream}

import korolev.Async
import korolev.internal.DevMode
import levsha.Id

import scala.collection.concurrent.TrieMap

abstract class StateStorage[F[+_]: Async, S] {

  def createTopLevelState: DeviceId => F[S]

  /**
    * Initialize a new state for a new session under the device
    * @param deviceId Identifier of device
    * @return Future with new state
    */
  def create(deviceId: DeviceId, sessionId: SessionId): F[StateManager[F]]

  /**
    * Restore session from storage
    * @return Future with result if session already exists
    */
  def get(deviceId: DeviceId, sessionId: SessionId): F[Option[StateManager[F]]]

  def remove(deviceId: DeviceId, sessionId: SessionId): Unit
}

object StateStorage {

  /**
    * Initializes a simple in-memory storage (based on TrieMap)
    * with equal initial state for all sessions.
    *
    * @param initialState State factory
    * @tparam S Type of state
    * @return The state storage
    */
  def default[F[+_]: Async, S: StateSerializer](initialState: => S): StateStorage[F, S] = {
    new DefaultStateStorage(_ => Async[F].pure(initialState))
  }

  /**
    * Initializes a simple in-memory storage (based on TrieMap)
    * with initial state based on deviceId
    *
    * {{{
    * case class MyState(deviceId: DeviceId, ...)
    *
    * StateStorage forDeviceId { deviceId =>
    *   MyStorage.getStateByDeviceId(deviceId) map {
    *     case Some(state) => state
    *     case None => MyState(deviceId, ...)
    *   }
    * }
    * }}}
    *
    * @param initialState State factory
    * @tparam S Type of state
    * @return The state storage
    */
  def forDeviceId[F[+_]: Async, S: StateSerializer](initialState: String => F[S]): StateStorage[F, S] = {
    new DefaultStateStorage(initialState)
  }

  private class DefaultStateStorage[F[+_]: Async, S: StateSerializer]
      (val createTopLevelState: String => F[S]) extends StateStorage[F, S] {

    private val cache = TrieMap.empty[String, StateManager[F]]

    def mkKey(deviceId: DeviceId, sessionId: SessionId): String = {
      s"$deviceId-$sessionId"
    }

    def get(deviceId: DeviceId, sessionId: SessionId): F[Option[StateManager[F]]] = {
      Async[F].pure(cache.get(mkKey(deviceId, sessionId)))
    }

    def create(deviceId: DeviceId, sessionId: SessionId): F[StateManager[F]] = {
      val key = mkKey(deviceId, sessionId)
      val sm = if (DevMode.isActive) {
        val directory = new File(DevMode.sessionsDirectory, key)
        new DevModeStateManager[F](directory)
      }
      else {
        new SimpleInMemoryStateManager[F]()
      }
      cache.put(key, sm)
      Async[F].pure(sm)
    }

    override def remove(deviceId: DeviceId, sessionId: SessionId): Unit = {
      cache.remove(mkKey(deviceId, sessionId))
      ()
    }
  }

  private final class DevModeStateManager[F[+_]: Async](directory: File) extends StateManager[F] {

    def getStateFile(node: Id): File =
      new File(directory, node.mkString)

    def getStateFileOpt(node: Id): Option[File] = {
      val file = getStateFile(node)
      if (file.exists()) Some(file) else None
    }

    def snapshot: F[StateManager.Snapshot] = Async[F].pure {
      new StateManager.Snapshot {

        val cache = directory
          .listFiles()
          .map { file => Id(file.getName) -> readFile(file) }
          .toMap

        def apply[T: StateDeserializer](nodeId: Id): Option[T] = {
          cache.get(nodeId) flatMap { data =>
            implicitly[StateDeserializer[T]].deserialize(data)
          }
        }
      }
    }

    def read[T: StateDeserializer](nodeId: Id): F[Option[T]] = Async[F].fork {
      getStateFileOpt(nodeId).flatMap { file =>
        val data = readFile(file)
        implicitly[StateDeserializer[T]].deserialize(data)
      }
    }


    def delete(nodeId: Id): F[Unit] = Async[F].fork {
      val file = getStateFile(nodeId)
      file.delete()
      ()
    }

    def write[T: StateSerializer](nodeId: Id, value: T): F[Unit] = Async[F].fork {
      val file = getStateFile(nodeId)
      if (!file.exists()) {
        file.getParentFile.mkdirs()
        file.createNewFile()
      }

      val outputStream = new FileOutputStream(file)
      val data = implicitly[StateSerializer[T]].serialize(value)
      outputStream.write(data)
    }

    def readFile(file: File): Array[Byte] = {
      val stream = new FileInputStream(file)
      val data = new Array[Byte](file.length().toInt)
      stream.read(data)
      data
    }
  }

  private final class SimpleInMemoryStateManager[F[+_]: Async] extends StateManager[F] {

    val cache = TrieMap.empty[Id, Any]

    val snapshot: F[StateManager.Snapshot] = Async[F].pureStrict {
      new StateManager.Snapshot {
        def apply[T: StateDeserializer](nodeId: Id): Option[T] = try {
          cache
            .get(nodeId)
            .asInstanceOf[Option[T]]
        } catch {
          case _: ClassCastException =>
            None
        }
      }
    }

    def read[T: StateDeserializer](nodeId: Id): F[Option[T]] =
      Async[F].map(snapshot)(_.apply(nodeId))

    def delete(nodeId: Id): F[Unit] = {
      cache.remove(nodeId)
      Async[F].unit
    }

    def write[T: StateSerializer](nodeId: Id, value: T): F[Unit] = {
      cache.put(nodeId, value)
      Async[F].unit
    }
  }
}
