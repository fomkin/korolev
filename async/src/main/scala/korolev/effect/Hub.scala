package korolev.effect

import java.util.concurrent.ConcurrentSkipListSet

import scala.util.{Failure, Success}

/**
  * A function which returns new streams which
  * contains same elements as the parent stream.
  * This is helpful when you want to consume
  * content of the stream in few different places.
  */
final class Hub[F[_]: Effect, T](stream: Stream[F, T], bufferSize: Int) extends (() => F[Stream[F, T]]) {

  private val queues = new ConcurrentSkipListSet[Queue[F, T]]()

  private val puller = (x: T) => Effect[F].delay {
    queues.forEach { q =>
      // 1 hour is means nothing because
      // offer is synchronous operation
      Effect[F].run(q.offer(x))
    }
  }

  // Run this stream with puller.
  // TODO pulling process should be demand based
  Effect[F].runAsync(stream.foreach(puller)) {
    // TODO rewrite with async
    case Success(_) => queues.forEach(q => Effect[F].run(q.close()))
    case Failure(e) => queues.forEach(q => Effect[F].run(q.fail(e)))
  }

  def apply(): F[Stream[F, T]] = Effect[F].delay {
    val queue = Queue[F, T](bufferSize)
    queues.add(queue)
    Effect[F].runAsync(queue.stream.consumed) { _ =>
      // Remove queue if it was canceled/closed
      queues.remove(queue)
    }
    queue.stream
  }
}

object Hub {

  /**
    * @see Hub
    */
  def apply[F[_]: Effect, T](stream: Stream[F, T], bufferSize: Int = Int.MaxValue): Hub[F, T] =
    new Hub(stream, bufferSize)
}