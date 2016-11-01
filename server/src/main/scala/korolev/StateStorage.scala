package korolev

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
trait StateStorage[T] {
  def read(id: String): Future[Option[T]]
  def write(id: String, value: T): Future[Unit]
}

object StateStorage {
  def inMemory[T]: StateStorage[T] = new StateStorage[T] {
    val storage = TrieMap.empty[String, T]
    def read(id: String): Future[Option[T]] =
      Future.successful(storage.get(id))
    def write(id: String, value: T): Future[Unit] = {
      storage.put(id, value)
      Future.successful(())
    }
  }
}
