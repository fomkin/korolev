package korolev.internal
import java.nio.ByteBuffer

import korolev.Async

/**
  * @param pull Function which should be invoked recursively until it return None.
  * @param finished Completes when all bytes was pulled
  * @param size known (or not) size
  */
final case class LazyBytes[F[_]](
    pull: () => F[Option[Array[Byte]]],
    finished: F[Unit],
    size: Option[Long])(implicit async: Async[F]) {

  /**
    * Folds all data to one byte array. Completes [[finished]].
    */
  def toStrict: F[Array[Byte]] = {
    def aux(acc: List[Array[Byte]]): F[List[Array[Byte]]] = {
      async.flatMap(pull()) {
        case Some(bytes) => aux(bytes :: acc)
        case None => async.pure(acc)
      }
    }
    async.map(aux(Nil)) { xs =>
      val length = xs.foldLeft(0)(_ + _.length)
      xs.foldRight(ByteBuffer.allocate(length))((a, b) => b.put(a)).array()
    }
  }

  /**
    * Drops all data. Completes [[finished]].
    */
  def discard(): F[Unit] = {
    def aux(): F[Unit] = async.flatMap(pull()) { x =>
      if (x.isEmpty) async.unit
      else aux()
    }
    aux()
  }
}

object LazyBytes {
  def empty[F[_]](implicit async: Async[F]): LazyBytes[F] = {
    val it = async.pure(Option.empty[Array[Byte]])
    LazyBytes(() => it, async.unit, Some(0L))
  }
}