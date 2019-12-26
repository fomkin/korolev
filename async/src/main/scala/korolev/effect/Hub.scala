package korolev.effect

import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicBoolean
import korolev.effect.syntax._

/**
  * A function which returns new streams which
  * contains same elements as the parent stream.
  * This is helpful when you want to consume
  * content of the stream in few different places.
  */
final class Hub[F[_]: Effect, T](upstream: Stream[F, T], bufferSize: Int) {

  private val queues = new ConcurrentSkipListSet[Queue[F, T]]()

  private val inProgress = new AtomicBoolean(false)

  @volatile private var closed = false

  private final class StreamOnePullAtTime(thisQueue: Queue[F, T]) extends Stream[F, T] {

    private def begin(): F[Boolean] =
      Effect[F].delay(inProgress.compareAndSet(false, true))

    private def end(): F[Unit] =
      Effect[F].delay {
        val end = inProgress.compareAndSet(true, false)
        if (!end) throw new IllegalStateException("Can't be false")
      }

    private def pullUpstream(): F[Option[T]] = upstream
      .pull()
      .map { maybeItem =>
        maybeItem match {
          case Some(item) =>
            queues.forEach { queue =>
              if (queue != thisQueue)
                queue.offerUnsafe(item)
            }
          case None =>
            closed = true
            queues.forEach(_.closeUnsafe())
        }
        maybeItem
      }

    def pull(): F[Option[T]] = begin()
      .flatMap { began =>
        if (!began) thisQueue.stream.pull()
        else for (result <- pullUpstream(); _ <- end())
          yield result
      }

    def cancel(): F[Unit] = thisQueue.close()
    def consumed: F[Unit] = ???
    def size: Option[Long] = ???
  }

  private final class QueueRemoveFromHubOnClose extends Queue[F, T](bufferSize) {
    override def closeUnsafe(): Unit = {
      queues.remove(this)
      super.closeUnsafe()
    }
  }

  def newStream(): F[Stream[F, T]] = Effect[F].delay {
    if (closed)
      throw new IllegalStateException("Hub is closed")
    val queue = new QueueRemoveFromHubOnClose()
    val stream = new StreamOnePullAtTime(queue)
    queues.add(queue)
    stream
  }
}

object Hub {

  /**
    * @see Hub
    */
  def apply[F[_]: Effect, T](stream: Stream[F, T], bufferSize: Int = Int.MaxValue): Hub[F, T] =
    new Hub(stream, bufferSize)
}