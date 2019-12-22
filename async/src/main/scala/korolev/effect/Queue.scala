package korolev.effect

import scala.collection.mutable

trait Queue[F[_], T] {
  def offer(item: T): F[Unit]
  def close(): F[Unit]
  def fail(e: Throwable): F[Unit]
  def stream: Stream[F, T]
}

object Queue {

  private final class DefaultQueueImpl[F[_]: Effect, T](maxSize: Int) extends Queue[F, T] {

    def offer(item: T): F[Unit] =
      Effect[F].delay {
        underlyingQueue.synchronized {
          if (underlyingQueue.size == maxSize) {
            // Remove head from queue if max size reached
            underlyingQueue.dequeue()
          }
          if (pending != null) {
            pending(Right(Some(item)))
            pending = null
          } else {
            underlyingQueue.enqueue(item)
          }
        }
      }

    def close(): F[Unit] =
      Effect[F].delay {
        this.synchronized {
          if (pending != null) {
            pending(Right(None))
            pending = null
          }
          if (finished != null)
            finished(Right(()))
          closed = true
        }
      }

    def fail(e: Throwable): F[Unit] =
      Effect[F].delay {
        this.synchronized {
          if (finished != null)
            finished(Left(e))
          error = e
        }
      }

    val stream: Stream[F, T] = new Stream[F, T] {

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

    var closed = false
    var error: Throwable = _
    var pending: Effect.Promise[Option[T]] = _
    var finished: Effect.Promise[Unit] = _
    val underlyingQueue: mutable.Queue[T] = mutable.Queue.empty[T]
  }

  def apply[F[_]: Effect, T](maxSize: Int = Int.MaxValue): Queue[F, T] =
    new DefaultQueueImpl[F, T](maxSize)
}