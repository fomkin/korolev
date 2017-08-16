package korolev.server

import java.io._

import korolev.Async
import slogging.LazyLogging

import scala.reflect.runtime.universe._
import scala.collection.concurrent.TrieMap
import korolev.DevMode
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
    * Restore session from storage on initialize a new one
    * @return Future with result if session already exists
    */
  def read[A: TypeTag](deviceId: DeviceId, sessionId: SessionId, node: Id): F[Option[A]]

  /**
    * Save session to storage
    * @return Future of successful saving
    */
  def write[A: TypeTag](deviceId: DeviceId, sessionId: SessionId, node: Id, value: A): F[A]
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

    val storage = TrieMap.empty[String, Any]

    def read[A: TypeTag](deviceId: DeviceId, sessionId: SessionId, node: Id): F[Option[A]] = {
      if (DevMode.isActive) {
        Async[F].fork {
          val file = getStateFile(deviceId, sessionId, node)
          if (file.exists) {
            val fileStream = new FileInputStream(file)
            val objectStream = new ObjectInputStream(fileStream)
            try {
              Some(
                objectStream
                  .readObject()
                  .asInstanceOf[A]
              )
            } catch {
              case _:InvalidClassException =>
                // That means state type was changed
                None
            } finally {
              objectStream.close()
              fileStream.close()
            }
          } else {
            None
          }
        }
      } else Async[F].pure {
        storage
          .get(mkKey(deviceId, sessionId, node))
          .asInstanceOf[Option[A]]
      }
    }

    def write[A: TypeTag](deviceId: DeviceId, sessionId: SessionId, node: Id, value: A): F[A] = {
      if (DevMode.isActive) {
        Async[F].fork {
          val file = getStateFile(deviceId, sessionId, node)
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
        storage.put(mkKey(deviceId, sessionId, node), value)
        Async[F].pure(value)
      }
    }

    def mkKey(deviceId: DeviceId, sessionId: SessionId, node: Id): String =
      s"$deviceId-$sessionId-${node.mkString}"

    def getStateFile(deviceId: DeviceId, sessionId: SessionId, node: Id): File =
      new File(DevMode.sessionsDirectory, mkKey(deviceId, sessionId, node))
  }
}
