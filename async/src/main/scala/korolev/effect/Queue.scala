package korolev.effect

import scala.collection.mutable
import scala.util.{Failure, Success}

final case class Queue[F[_]: Effect, T](offer: T => F[Unit],
                                        close: () => F[Unit],
                                        fail: Throwable => F[Unit],
                                        stream: Stream[F, T])

object Queue {

  private final class QueueBackend[F[_]: Effect, T](maxSize: Int) {

    private var closed = false
    private var error: Throwable = _
    private var pending: Effect.Promise[F, Option[T]] = _

    private val finished = Effect[F].promise[Unit]
    private val underlyingQueue = mutable.Queue[T]()

    private val close = () => Effect[F].delay {
      this.synchronized {
        if (pending != null) {
          pending.complete(Success(None))
          pending = null
        }
        finished.complete(Success(()))
        closed = true
      }
    }

    val queue: Queue[F, T] = Queue(
      offer = x => Effect[F].delay {
        this.synchronized {
          if (underlyingQueue.size == maxSize) {
            // Remove head from queue if max size reached
            underlyingQueue.dequeue()
          }
          if (pending != null) {
            pending.complete(Success(Some(x)))
            pending = null
          } else {
            underlyingQueue.enqueue(x)
          }
        }
      },
      close = close,
      fail = e => Effect[F].delay {
        this.synchronized {
          finished.complete(Failure(e))
          error = e
        }
      },
      stream = Stream[F, T](
        pull = () => {
          val xx = Effect[F].delay {
            this.synchronized {
              if (!closed) {
                if (underlyingQueue.nonEmpty) {
                  Effect[F].delay(Option(underlyingQueue.dequeue()))
                }
                else {
                  pending = Effect[F].promise[Option[T]]
                  pending.effect
                }
              }
              else Effect[F].pure(Option.empty[T])
            }
          }
          Effect[F].flatMap(xx)(identity)
        },
        finished = finished.effect,
        cancel = close,
        size = None
      )
    )

  }

  def apply[F[_]: Effect, T](maxSize: Int = Int.MaxValue): Queue[F, T] = {
    val backend = new QueueBackend[F, T](maxSize)
    backend.queue
  }
}