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
        this.synchronized {
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

      def pull(): F[Option[T]] = {
        val effect = Effect[F].delay {
          this.synchronized {
            if (!closed) {
              pending = null // Reset pending request
              if (underlyingQueue.nonEmpty) {
                Effect[F].delay(Option(underlyingQueue.dequeue()))
              } else {
                createPendingRequest()
              }
            }
            else {
              Effect[F].pure(Option.empty[T])
            }
          }
        }
        Effect[F].flatMap(effect)(identity)
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

    def createPendingRequest(): F[Option[T]] = {
      Effect[F].promise[Option[T]] { cb =>
        this.synchronized {
          if (underlyingQueue.nonEmpty) {
            // Element had been added to queue
            // between pull() invocation and effect run.
            // Resolve promise immediately.
            cb(Right(Option(underlyingQueue.dequeue())))
          } else {
            // Save callback to invoke in the future
            pending = cb
          }
        }
      }
    }
  }

  def apply[F[_]: Effect, T](maxSize: Int = Int.MaxValue): Queue[F, T] =
    new DefaultQueueImpl[F, T](maxSize)
}