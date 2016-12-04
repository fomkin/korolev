package korolev

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
trait StateStorage[T] {

  /**
    * Initialize a new state for a new session under the device
    * @param deviceId Identifier of device
    * @return Future with new state
    */
  def initial(deviceId: String): Future[T]

  /**
    * Restore session from storage
    * @return Future with result if session
    *         already exists or future
    *         with None with if doesn't
    */
  def read(deviceId: String, sessionId: String): Future[T]

  /**
    * Save session to storage
    * @return Future of successful saving
    */
  def write(deviceId: String, sessionId: String, value: T): Future[Unit]
}

object StateStorage {

  /**
    * Initialize simple in-memory storage (based on TrieMap).
    * @param initialState State factory
    * @tparam T Type of state
    * @return The state storage
    */
  def default[T](initialState: => T): StateStorage[T] = new StateStorage[T] {

    val storage = TrieMap.empty[String, T]

    def read(deviceId: String, sessionId: String): Future[T] = {
      val state = storage.getOrElseUpdate(deviceId + sessionId, initialState)
      Future.successful(state)
    }

    def write(deviceId: String, sessionId: String, value: T): Future[Unit] = {
      storage.put(deviceId + sessionId, value)
      Future.successful(())
    }

    def initial(deviceId: String): Future[T] = Future.successful(initialState)
  }
}
