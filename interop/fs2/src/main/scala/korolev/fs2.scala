package korolev

import _root_.cats.effect.{ConcurrentEffect => CatsConcurrentEffect}
import _root_.fs2.{Stream => Fs2Stream}
import korolev.effect.syntax._
import korolev.effect.{Queue, Effect => KorolevEffect, Stream => KorolevStream}

import scala.concurrent.ExecutionContext

object fs2 {

  implicit class Fs2StreamOps[F[_] : KorolevEffect : CatsConcurrentEffect, O](stream: Fs2Stream[F, O]) {

    def toKorolev(bufferSize: Int = 1)(implicit ec: ExecutionContext): F[KorolevStream[F, O]] = {
      val queue = new Queue[F, O](bufferSize)
      val cancelToken: Either[Throwable, Unit] = Right(())

      KorolevEffect[F]
        .start(
          stream
            .interruptWhen(queue.cancelSignal.as(cancelToken))
            .evalMap(queue.enqueue)
            .compile
            .drain
            .flatMap(_ => queue.stop())
        )
        .as(queue.stream)
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
