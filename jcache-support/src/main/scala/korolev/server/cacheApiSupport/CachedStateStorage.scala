package korolev.server.cacheApiSupport

import javax.cache.Cache

import korolev.{Async, StateReader}
import korolev.server.StateStorage
import korolev.server.StateStorage.{DeviceId, SessionId}
import levsha.Id


/**
  * State storage based on
  * <a href="https://jcp.org/aboutJava/communityprocess/final/jsr107/index.html">
  *   Java Temporary Caching API (JSR-107)
  * </a>.
  *
  * In production we work with huge number of session which cannot be
  * served by single node. When servers number is more than one our
  * system become distributed. It means we need to synchronize state
  * between nodes. CachedStateStorage helps to solve this problem.
  *
  * CachedStateStorage uses standard Java Caching API allows your app
  * to work with any in-memory cache which is support that standard.
  *
  * {{{
  * val cachingProvider = Caching.getCachingProvider()
  * val cacheManager = cachingProvider.getCacheManager()
  * val cache = cacheManager.getCache("default", classOf[String], classOf[Any])
  *
  * CachedStateStorage[Future, MyState] (cache) { deviceId =>
  *  ...
  * }
  * }}}
  *
  * Note that creating instance of javax.cache.Cache for different
  * data grids can differ greatly. See examples in Korolev repository.
  *
  * @see <a href="https://jcp.org/aboutJava/communityprocess/final/jsr107/index.html">JSR-107 Specification</a>
  * @see <a href="https://jcp.org/aboutJava/communityprocess/implementations/jsr107/index.html">List of JSR-107 implementations</a>
  */
final class CachedStateStorage[F[+_]: Async, T](cache: Cache[String, Any], initialState: DeviceId => F[T])
  extends StateStorage[F, T] {

  if (cache == null)
    throw new NullPointerException("cache shouldn't be null")

  def createTopLevelState(deviceId: DeviceId): F[T] =
    initialState(deviceId)

  def readAll(deviceId: DeviceId, sessionId: SessionId): F[Option[StateReader]] = Async[F] fork {
    readIds(deviceId, sessionId) map { ids =>
      val values = ids.flatMap(id => readStrict(deviceId, sessionId, id))
      val table = ids.zip(values).toMap
      new StateReader {
        def read[A](node: Id): Option[A] = {
          table.get(node).asInstanceOf[Option[A]]
        }
      }
    }
  }

  def read[A](deviceId: DeviceId, sessionId: SessionId, node: Id): F[Option[A]] = Async[F] fork {
    readStrict(deviceId, sessionId, node).asInstanceOf[Option[A]]
  }

  def write[A](deviceId: DeviceId, sessionId: SessionId, node: Id, value: A): F[A] = Async[F] fork {
    val updatedIds = readIds(deviceId, sessionId).getOrElse(Set.empty) + node
    cache.put(idsKey(deviceId, sessionId), updatedIds.map(_.mkString))
    cache.put(key(deviceId, sessionId, node), value)
    value
  }

  def readStrict(deviceId: DeviceId, sessionId: SessionId, node: Id): Option[Any] =
    Option(cache.get(key(deviceId, sessionId, node)))

  private def key(deviceId: DeviceId, sessionId: SessionId, node: Id): String =
    s"$deviceId-$sessionId-${node.mkString}"

  private def idsKey(deviceId: DeviceId, sessionId: SessionId): String =
    s"$deviceId-$sessionId-keys"

  private def readIds(deviceId: DeviceId, sessionId: SessionId): Option[Set[Id]] = {
    val key = idsKey(deviceId, sessionId)
    Option(cache.get(key).asInstanceOf[Set[String]]).map(_.map(key => Id(key)))
  }
}

object CachedStateStorage {
  def apply[F[+_]: Async, T](cache: Cache[String, Any])(initialState: DeviceId => F[T]): CachedStateStorage[F, T] =
    new CachedStateStorage(cache, initialState)
}
