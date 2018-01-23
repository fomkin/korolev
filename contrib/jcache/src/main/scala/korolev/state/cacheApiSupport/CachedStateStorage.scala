package korolev.state.cacheApiSupport

import javax.cache.Cache
import javax.cache.processor.{EntryProcessor, MutableEntry}

import korolev.Async
import korolev.state._
import levsha.Id

import scala.collection.JavaConverters._

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
final class CachedStateStorage[F[+_]: Async, S]
    (cache: Cache[String, Array[Byte]], _createTopLevelState: DeviceId => F[S])
    (implicit val keysW: StateSerializer[Set[String]], val keysR: StateDeserializer[Set[String]])
  extends StateStorage[F, S] {

  def createTopLevelState(deviceId: DeviceId, sessionId: SessionId): F[S] =
    _createTopLevelState(deviceId)

  private class CachedStateManager(deviceId: DeviceId, sessionId: SessionId) extends StateManager[F] {

    def snapshot: F[StateManager.Snapshot] = Async[F].fork {
      val keys = Option(cache.get(mkKeys(deviceId, sessionId)))
        .flatMap(keysR.deserialize)
        .getOrElse(Set.empty)
      new StateManager.Snapshot {
        val snapshotData = cache.getAll(keys.asJava).asScala
        def apply[T: StateDeserializer](nodeId: Id): Option[T] = snapshotData
          .get(mkKey(nodeId))
          .flatMap(implicitly[StateDeserializer[T]].deserialize)
      }
    }

    def read[T: StateDeserializer](nodeId: Id): F[Option[T]] = Async[F].fork {
      Option(cache.get(mkKey(nodeId))).flatMap { data =>
        implicitly[StateDeserializer[T]].deserialize(data)
      }
    }

    def write[T: StateSerializer](nodeId: Id, value: T): F[Unit] = Async[F].fork {
      val data = implicitly[StateSerializer[T]].serialize(value)
      val key = mkKey(nodeId)
      cache.put(key, data)
      cache.invoke(mkKeys(deviceId, sessionId), new AddKeyProcessor(key))
    }

    private class AddKeyProcessor(key: String) extends EntryProcessor[String, Array[Byte], Unit] {
      def process(entry: MutableEntry[String, Array[Byte]], arguments: AnyRef*): Unit = {
        val data = if (entry.exists()) entry.getValue else emptyKeysData
        val keys = keysR.deserialize(data).getOrElse(Set.empty)
        entry.setValue(keysW.serialize(keys + key))
      }
    }

    private def mkKey(nodeId: Id) = {
      s"${mkKeys(deviceId, sessionId)}-${nodeId.mkString}"
    }
  }

  if (cache == null)
    throw new NullPointerException("cache shouldn't be null")

  private val emptyKeysData = keysW.serialize(Set.empty[String])

  def get(deviceId: DeviceId, sessionId: SessionId): F[Option[StateManager[F]]] = Async[F] fork {
    Option(cache.get(mkKeys(deviceId, sessionId))).map { _ =>
      new CachedStateManager(deviceId, sessionId)
    }
  }

  def create(deviceId: DeviceId, sessionId: SessionId): F[StateManager[F]] = Async[F] fork {
    cache.put(mkKeys(deviceId, sessionId), emptyKeysData)
    new CachedStateManager(deviceId, sessionId)
  }

  private def mkKeys(deviceId: DeviceId, sessionId: SessionId) = {
    s"$deviceId-$sessionId"
  }
}

object CachedStateStorage {
  def apply[F[+_]: Async, T]
      (cache: Cache[String, Array[Byte]])
      (initialState: DeviceId => F[T])
      (implicit keysW: StateSerializer[Set[String]], keysR: StateDeserializer[Set[String]]): CachedStateStorage[F, T] =
    new CachedStateStorage(cache, initialState)
}
