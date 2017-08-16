package korolev.server

import java.io._

import korolev.{Async, DevMode, StateReader}
import slogging.LazyLogging

import scala.collection.concurrent.TrieMap
import levsha.Id

abstract class StateStorage[F[+_]: Async, T] {

  import StateStorage.{DeviceId, SessionId}

  /**
    * Initialize a new state for a new session under the device
    * @param deviceId Identifier of device
    * @return Future with new state
    */
  def createTopLevelState(deviceId: DeviceId): F[T]

  /**
    * Restore all state for session
    * @return Future with reader
    */
  def readAll(deviceId: DeviceId, sessionId: SessionId): F[Option[StateReader]]

  /**
    * Restore session from storage on initialize a new one
    * @return Future with result if session already exists
    */
  def read[A](deviceId: DeviceId, sessionId: SessionId, node: Id): F[Option[A]]

  /**
    * Save session to storage
    * @return Future of successful saving
    */
  def write[A](deviceId: DeviceId, sessionId: SessionId, node: Id, value: A): F[A]
}

object StateStorage extends LazyLogging {

  type DeviceId = String
  type SessionId = String

  /**
    * Initializes a simple in-memory storage (based on TrieMap)
    * with equal initial state for all sessions.
    *
    * @param initialState State factory
    * @tparam T Type of state
    * @return The state storage
    */
  def default[F[+_]: Async, T](initialState: => T): StateStorage[F, T] = new DefaultStateStorage[F, T] {
    def createTopLevelState(deviceId: String): F[T] = Async[F].pure(initialState)
  }

  /**
    * Initializes a simple in-memory storage (based on TrieMap)
    * with initial state based on deviceId
    *
    * {{{
    * case class MyState(deviceId: String, ...)
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
    * @tparam T Type of state
    * @return The state storage
    */
  def forDeviceId[F[+_]: Async, T](initialState: DeviceId => F[T]): StateStorage[F, T] = new DefaultStateStorage[F, T] {
    def createTopLevelState(deviceId: String): F[T] = initialState(deviceId)
  }

  private abstract class DefaultStateStorage[F[+_]: Async, T] extends StateStorage[F, T] {

    val async = Async[F]

    val storage = TrieMap.empty[String, TrieMap[Id, Any]]

    def readFromFile(file: File): Option[Any] = {
      val fileStream = new FileInputStream(file)
      val objectStream = new ObjectInputStream(fileStream)
      try Some {
        objectStream.readObject()
      } catch {
        case _:InvalidClassException =>
          // That means state type was changed
          None
      } finally {
        objectStream.close()
        fileStream.close()
      }
    }

    def readAll(deviceId: DeviceId, sessionId: SessionId): F[Option[StateReader]] = {
      if (DevMode.isActive) {
        async.fork {
          getSessionDirectory(deviceId, sessionId) map { dir =>
            val map = dir.list()
              .toList
              .flatMap { name =>
                val node = Id(name)
                getStateFileOpt(dir, node)
                  .flatMap(readFromFile)
                  .map(x => node -> x)
              }
              .toMap
            new StateReader {
              def read[A](node: Id): Option[A] =
                map.get(node).asInstanceOf[Option[A]]
            }
          }
        }
      } else async.pure {
        storage.get(mkKey(deviceId, sessionId)) map { states =>
          new StateReader {
            def read[A](node: Id): Option[A] =
              states.get(node).asInstanceOf[Option[A]]
          }
        }
      }
    }

    def read[A](deviceId: DeviceId, sessionId: SessionId, node: Id): F[Option[A]] = {
      if (DevMode.isActive) {
        Async[F].fork {
          getSessionDirectory(deviceId, sessionId) flatMap { dir =>
            getStateFileOpt(dir, node) flatMap { file =>
              readFromFile(file).asInstanceOf[Option[A]]
            }
          }
        }
      } else Async[F].pure {
        val states = storage.getOrElseUpdate(mkKey(deviceId, sessionId), TrieMap.empty)
        states.get(node).asInstanceOf[Option[A]]
      }
    }

    def write[A](deviceId: DeviceId, sessionId: SessionId, node: Id, value: A): F[A] = {
      if (DevMode.isActive) {
        Async[F].fork {
          val dir = createSessionDirectory(deviceId, sessionId)
          val file = getStateFile(dir, node)
          val fileStream = new FileOutputStream(file)
          val objectStream = new ObjectOutputStream(fileStream)
          try {
            objectStream.writeObject(value)
            value
          } finally {
            objectStream.close()
            fileStream.close()
          }
        }
      } else {
        val states = storage.getOrElseUpdate(mkKey(deviceId, sessionId), TrieMap.empty)
        states.put(node, value)
        Async[F].pure(value)
      }
    }

    def mkKey(deviceId: DeviceId, sessionId: SessionId): String =
      s"$deviceId-$sessionId"

    def getSessionDirectory(deviceId: DeviceId, sessionId: SessionId): Option[File] = {
      val file = new File(DevMode.sessionsDirectory, mkKey(deviceId, sessionId))
      if (!file.isDirectory) file.delete()
      if (file.exists()) Some(file) else None
    }

    def createSessionDirectory(deviceId: DeviceId, sessionId: SessionId): File = {
      val file = new File(DevMode.sessionsDirectory, mkKey(deviceId, sessionId))
      if (!file.isDirectory) file.delete()
      if (!file.exists()) file.mkdir()
      file
    }

    def getStateFile(dir: File, node: Id): File = new File(dir, node.mkString)

    def getStateFileOpt(dir: File, node: Id): Option[File] = {
      val file = getStateFile(dir, node)
      if (file.exists()) Some(file) else None
    }
  }
}
