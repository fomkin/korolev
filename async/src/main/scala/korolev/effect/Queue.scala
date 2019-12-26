package korolev.effect

import scala.collection.mutable

class Queue[F[_]: Effect, T](maxSize: Int) {

  def offer(item: T): F[Unit] =
    Effect[F].delay(offerUnsafe(item))

  def offerUnsafe(item: T): Unit =
    underlyingQueue.synchronized {
      if (underlyingQueue.size == maxSize) {
        // Remove head from queue if max size reached
        underlyingQueue.dequeue()
      }
      if (pending != null) {
        val cb = pending
        pending = null
        cb(Right(Some(item)))
      } else {
        underlyingQueue.enqueue(item)
      }
    }

  def close(): F[Unit] =
    Effect[F].delay(closeUnsafe())

  def closeUnsafe(): Unit =
    this.synchronized { // FIXME sync on underlyingQueue?
      if (pending != null) {
        val cb = pending
        pending = null
        cb(Right(None))
      }
      if (finished != null)
        finished(Right(()))
      closed = true
    }

  def fail(e: Throwable): F[Unit] =
    Effect[F].delay {
      this.synchronized {
        if (finished != null)
          finished(Left(e))
        error = e
      }
    }

  private final class QueueStream extends Stream[F, T] {

    def pull(): F[Option[T]] = Effect[F].promise { cb =>
      underlyingQueue.synchronized {
        if (closed) cb(Right(None)) else {
          if (underlyingQueue.nonEmpty) {
            val elem = underlyingQueue.dequeue()
            cb(Right(Some(elem)))
          } else {
            pending = cb
          }
        }
      }
    }

    val consumed: F[Unit] = Effect[F].promise[Unit] { cb =>
      if (error != null) cb(Left(error))
      else if (closed) cb(Right(()))
      else finished = cb
    }

    def cancel(): F[Unit] = close()

    val size: Option[Long] = None
  }

  val stream: Stream[F, T] = new QueueStream()

  private var closed = false
  private var error: Throwable = _
  @volatile private var pending: Effect.Promise[Option[T]] = _
  private var finished: Effect.Promise[Unit] = _
  private val underlyingQueue: mutable.Queue[T] = mutable.Queue.empty[T]
}

object Queue {

  def apply[F[_]: Effect, T](maxSize: Int = Int.MaxValue): Queue[F, T] =
    new Queue[F, T](maxSize)
}