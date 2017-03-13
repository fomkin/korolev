package korolev.server.cacheApiSupport

import javax.cache.Cache

import korolev.Async
import korolev.Async._
import korolev.server.StateStorage
import korolev.server.StateStorage.{DeviceId, SessionId}

import scala.language.higherKinds

/**
  * State storage based on Java Caching API (JSR-107).
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
  * CachedStateStorage(cache) { deviceId =>
  *  ...
  * }
  * }}}
  *
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
class CachedStateStorage[F[+_]: Async, T](cache: Cache[String, T], initialState: DeviceId => F[T]) extends StateStorage[F, T] {

  def initial(deviceId: DeviceId): F[T] =
    initialState(deviceId)

  def read(deviceId: DeviceId, sessionId: SessionId): F[T] = {
    val valueOpt = Async[F] fork {
      val key = deviceId + sessionId
      Option(cache.get(key))
    }
    valueOpt flatMap {
      case Some(value) => Async[F].pure(value)
      case None => initialState(deviceId)
    }
  }

  def write(deviceId: DeviceId, sessionId: SessionId, value: T): F[T] = Async[F] fork {
    val key = deviceId + sessionId
    cache.put(key, value)
    value
  }
}

object CachedStateStorage {
  def apply[F[_]: Async, T](cache: Cache[String, T])(initialState: DeviceId => F[T]): CachedStateStorage[F, T] =
    new CachedStateStorage(cache, initialState)
}
