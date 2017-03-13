package korolev.server

import korolev.async.Async

import scala.collection.concurrent.TrieMap
import scala.language.higherKinds

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
abstract class StateStorage[F[+_]: Async, T] {

  import StateStorage.{DeviceId, SessionId}

  /**
    * Initialize a new state for a new session under the device
    * @param deviceId Identifier of device
    * @return Future with new state
    */
  def initial(deviceId: DeviceId): F[T]

  /**
    * Restore session from storage on initialize a new one
    * @return Future with result if session
    *         already exists or future
    *         with None with if doesn't
    */
  def read(deviceId: DeviceId, sessionId: SessionId): F[T]

  /**
    * Save session to storage
    * @return Future of successful saving
    */
  def write(deviceId: DeviceId, sessionId: SessionId, value: T): F[T]
}

object StateStorage {

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
  def default[F[+_]: Async, T](initialState: => T): StateStorage[F, T] = new StateStorage[F, T] {

    val storage = TrieMap.empty[String, T]

    def read(deviceId: DeviceId, sessionId: SessionId): F[T] = {
      val state = storage.getOrElseUpdate(deviceId + sessionId, initialState)
      Async[F].pure(state)
    }

    def write(deviceId: String, sessionId: String, value: T): F[T] = {
      storage.put(deviceId + sessionId, value)
      Async[F].pure(value)
    }

    def initial(deviceId: String): F[T] = Async[F].pure(initialState)
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
  def forDeviceId[F[+_]: Async, T](initialState: DeviceId => F[T]): StateStorage[F, T] = new StateStorage[F, T] {

    val storage = TrieMap.empty[String, F[T]]

    def read(deviceId: DeviceId, sessionId: SessionId): F[T] = {
      storage.getOrElseUpdate(deviceId + sessionId, initialState(deviceId))
    }

    def write(deviceId: String, sessionId: String, value: T): F[T] = {
      val valueF = Async[F].pure(value)
      storage.put(deviceId + sessionId, valueF)
      valueF
    }

    def initial(deviceId: String): F[T] = initialState(deviceId)
  }

}
