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

import java.io.{File, FileInputStream, FileOutputStream}
import java.util

import korolev.effect.Effect
import korolev.internal.DevMode
import levsha.Id

import scala.collection.concurrent.TrieMap

abstract class StateStorage[F[_]: Effect, S] {

  /**
    * Check if state manager for the session is exist
    */
  def exists(deviceId: DeviceId, sessionId: SessionId): F[Boolean]

  /**
   * Create new state manager
   */
  def create(deviceId: DeviceId, sessionId: SessionId, topLevelState: S): F[StateManager[F]]

  /**
    * Restore session manager from storage
    */
  def get(deviceId: DeviceId, sessionId: SessionId): F[StateManager[F]]

  /**
    * Marks session to remove
    */
  def remove(deviceId: DeviceId, sessionId: SessionId): Unit
}

object StateStorage {

  private [korolev] final class DefaultStateStorage[F[_]: Effect, S: StateSerializer](forDeletionCacheCapacity: Int)
    extends StateStorage[F, S] {

    private val cache = TrieMap.empty[String, StateManager[F]]
    private val mutex = new Object()
    private val forDeletionCache = {
      new util.LinkedHashMap[String, StateManager[F]](forDeletionCacheCapacity, 0.7F, true) {
        override def removeEldestEntry(entry: java.util.Map.Entry[String, StateManager[F]]): Boolean = {
          this.size() > forDeletionCacheCapacity
        }
      }
    }

    def mkKey(deviceId: DeviceId, sessionId: SessionId): String = {
      s"$deviceId-$sessionId"
    }

    def exists(deviceId: DeviceId, sessionId: SessionId): F[Boolean] = {
      val key = mkKey(deviceId, sessionId)
      if (DevMode.isActive) {
        val file = new File(DevMode.sessionsDirectory, key)
        val result = cache.contains(key) || forDeletionCache.containsKey(key) || file.exists()
        Effect[F].delay(result)
      } else {
        val result = cache.contains(key) || forDeletionCache.containsKey(key)
        Effect[F].delay(result)
      }
    }

    def get(deviceId: DeviceId, sessionId: SessionId): F[StateManager[F]] = {
      def errored = Effect[F]
        .fail[StateManager[F]](new NoSuchElementException(s"There is no state for $deviceId/$sessionId"))
      val key = mkKey(deviceId, sessionId)
      cache.get(key) match {
        case None =>
          Option(mutex.synchronized(forDeletionCache.remove(key))) match {
            case Some(sm) =>
              Effect[F].delay {
                cache.put(key, sm)
                sm
              }
            case None =>
              if (DevMode.isActive) {
                val directory = new File(DevMode.sessionsDirectory, key)
                if (directory.exists()) {
                  val sm = new DevModeStateManager[F](directory)
                  cache.put(key, sm)
                  Effect[F].delay(sm)
                } else errored
              } else errored
          }
        case Some(sm) => Effect[F].delay(sm)
      }
    }

    def create(deviceId: DeviceId, sessionId: SessionId, state: S): F[StateManager[F]] = {
      val key = mkKey(deviceId, sessionId)
      if (DevMode.isActive) {
        val directory = new File(DevMode.sessionsDirectory, key)
        val sm = new DevModeStateManager[F](directory)
        cache.put(key, sm)
        if (directory.exists()) Effect[F].delay(sm) // Do not rewrite state manager cache
        else Effect[F].map(sm.write(Id.TopLevel, state))(_ => sm)
      }
      else {
        val sm = new SimpleInMemoryStateManager[F]()
        cache.put(key, sm)
        Effect[F].map(sm.write(Id.TopLevel, state))(_ => sm)
      }
    }

    override def remove(deviceId: DeviceId, sessionId: SessionId): Unit = {
      val key = mkKey(deviceId, sessionId)
      cache.remove(key) foreach { sm =>
        mutex.synchronized {
          forDeletionCache.put(key, sm)
        }
      }
    }
  }

  private final class DevModeStateManager[F[_]: Effect](directory: File) extends StateManager[F] {

    def getStateFile(node: Id): File =
      new File(directory, node.mkString)

    def getStateFileOpt(node: Id): Option[File] = {
      val file = getStateFile(node)
      if (file.exists()) Some(file) else None
    }

    def snapshot: F[StateManager.Snapshot] = Effect[F].delay {
      new StateManager.Snapshot {

        if (!directory.exists())
          directory.mkdirs()

        val cache: Map[Id, Array[Byte]] = directory
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

    def read[T: StateDeserializer](nodeId: Id): F[Option[T]] = Effect[F].delay {
      getStateFileOpt(nodeId).flatMap { file =>
        val data = readFile(file)
        implicitly[StateDeserializer[T]].deserialize(data)
      }
    }

    def delete(nodeId: Id): F[Unit] = Effect[F].delay {
      val file = getStateFile(nodeId)
      file.delete()
      ()
    }

    def write[T: StateSerializer](nodeId: Id, value: T): F[Unit] = Effect[F].delay {
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

  private[korolev] final class SimpleInMemoryStateManager[F[_]: Effect] extends StateManager[F] {

    val cache: TrieMap[Id, Any] = TrieMap.empty[Id, Any]

    val snapshot: F[StateManager.Snapshot] = Effect[F].pure {
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
      Effect[F].map(snapshot)(_.apply(nodeId))

    def delete(nodeId: Id): F[Unit] =
      Effect[F].delay {
        cache.remove(nodeId)
        ()
      }

    def write[T: StateSerializer](nodeId: Id, value: T): F[Unit] =
      Effect[F].delay {
        cache.put(nodeId, value)
        ()
      }
  }

  def apply[F[_]: Effect, S: StateSerializer](forDeletionCacheCapacity: Int = 5000): StateStorage[F, S] = {
    new DefaultStateStorage[F, S](forDeletionCacheCapacity)
  }
}
