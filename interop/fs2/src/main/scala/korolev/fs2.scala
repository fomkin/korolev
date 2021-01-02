package korolev

import _root_.fs2.{Stream => Fs2Stream}
import _root_.cats.effect.{ConcurrentEffect => CatsConcurrentEffect}
import korolev.effect.Effect.Promise
import korolev.effect.{Effect => KorolevEffect, Stream => KorolevStream}
import korolev.effect.syntax._

import scala.collection.mutable
import scala.concurrent.ExecutionContext

object fs2 {

  private class AsyncBlockingQueue[F[_] : KorolevEffect, O](maxSize: Int) extends KorolevStream[F, O] {

    import AsyncBlockingQueue._

    private val queue = mutable.Queue.empty[O]

    @volatile private var pullCallbacks = List.empty[Promise[Option[O]]]
    @volatile private var joinCallbacks = List.empty[Promise[Unit]]
    @volatile private var cancelCallbacks = List.empty[Promise[Unit]]

    def join: F[Unit] = KorolevEffect[F].delayAsync {
      this.synchronized {
        if (queue.size >= maxSize) {
          KorolevEffect[F].promise { cb =>
            joinCallbacks = cb :: joinCallbacks
          }
        } else {
          KorolevEffect[F].unit
        }
      }
    }

    def offer(value: O): F[Unit] = KorolevEffect[F].delay {
      this.synchronized {
        pullCallbacks match {
          case Nil if queue.size >= maxSize =>
            queue.dequeue()
            queue.enqueue(value)
          case Nil =>
            queue.enqueue(value)
          case xs =>
            val x = Right(Some(value))
            pullCallbacks = Nil
            xs.foreach(_ (x))
        }
      }
    }

    def pull(): F[Option[O]] = KorolevEffect[F].promise { cb =>
      this.synchronized {
        if (queue.nonEmpty) {
          val xs = joinCallbacks
          joinCallbacks = Nil
          cb(Right(Option(queue.dequeue())))
          xs.foreach(_ (token))
        } else {
          pullCallbacks = cb :: pullCallbacks
        }
      }
    }

    def cancelSignal: F[Unit] = KorolevEffect[F].promise { cb =>
      this.synchronized {
        cancelCallbacks = cb :: cancelCallbacks
      }
    }

    def cancel(): F[Unit] = KorolevEffect[F].delay {
      this.synchronized {
        val xs = cancelCallbacks
        cancelCallbacks = Nil
        xs.foreach(_ (token))
      }
    }
  }

  private object AsyncBlockingQueue {
    private final val token = Right(())
  }

  implicit class Fs2StreamOps[F[_] : KorolevEffect : CatsConcurrentEffect, O](stream: Fs2Stream[F, O]) {

    def toKorolev(bufferSize: Int = 1)(implicit ec: ExecutionContext): F[KorolevStream[F, O]] = {
      val queue = new AsyncBlockingQueue[F, O](bufferSize)
      val cancelToken: Either[Throwable, Unit] = Right(())

      KorolevEffect[F]
        .fork(
          stream
            .interruptWhen(queue.cancelSignal.as(cancelToken))
            .evalMap(o => queue.join.flatMap(_ => queue.offer(o)))
            .compile
            .drain
        )
        .as(queue)
    }
  }

  implicit class KorolevStreamOps[F[_] : KorolevEffect, O](stream: KorolevStream[F, O]) {
    def toFs2: Fs2Stream[F, O] =
      Fs2Stream.unfoldEval(()) { _ =>
        stream
          .pull()
          .map(mv => mv.map(v => (v, ())))
      }
  }

}
