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

    val cache = TrieMap.empty[String, StateManager[F]]

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
          .list()
          .map { name => Id(name) -> readFile(new File(name)) }
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

    def write[T: StateSerializer](nodeId: Id, value: T): F[Unit] = Async[F].fork {
      val file = getStateFile(nodeId)
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

    def write[T: StateSerializer](nodeId: Id, value: T): F[Unit] = {
      cache.put(nodeId, value)
      Async[F].unit
    }
  }
}
