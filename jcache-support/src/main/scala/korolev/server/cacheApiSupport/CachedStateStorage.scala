package korolev.server.cacheApiSupport

import javax.cache.Cache

import korolev.Async
import korolev.server.StateStorage
import korolev.server.StateStorage.{DeviceId, SessionId}


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
  * val cache = cacheManager.getCache("default", classOf[String], classOf[MyState])
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
final class CachedStateStorage[F[+_]: Async, T](cache: Cache[String, T], initialState: DeviceId => F[T])
  extends StateStorage[F, T] {

  if (cache == null)
    throw new NullPointerException("cache shouldn't be null")

  def initial(deviceId: DeviceId): F[T] =
    initialState(deviceId)

  def read(deviceId: DeviceId, sessionId: SessionId): F[Option[T]] = Async[F] fork {
    val key = deviceId + sessionId
    Option(cache.get(key))
  }

  def write(deviceId: DeviceId, sessionId: SessionId, value: T): F[T] = Async[F] fork {
    val key = deviceId + sessionId
    cache.put(key, value)
    value
  }
}

object CachedStateStorage {
  def apply[F[+_]: Async, T](cache: Cache[String, T])(initialState: DeviceId => F[T]): CachedStateStorage[F, T] =
    new CachedStateStorage(cache, initialState)
}
