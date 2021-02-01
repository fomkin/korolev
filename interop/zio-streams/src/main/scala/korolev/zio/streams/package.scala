package korolev.zio

import korolev.effect.{Queue, Effect => KorolevEffect, Stream => KorolevStream}
import korolev.effect.syntax._
import zio.stream.ZStream
import zio.{RIO, ZIO}

import scala.concurrent.ExecutionContext

package object streams {


  implicit class KorolevtSreamOps[R, O](stream: KorolevStream[RIO[R, *], O]) {

    def toZStream[R1 <: R]: ZStream[R1, Throwable, O] =
      ZStream.managed {
        for {
          str <- ZIO
            .runtime[R1]
            .toManaged_
            .map { implicit runtime =>
                ZStream.unfoldM(()) { _ =>
                  stream
                    .pull()
                    .map(mv => mv.map(v => (v, ())))
                }
            }
        } yield str
      }.flatten
  }

  implicit class ZStreamOps[R, O](stream: ZStream[R, Throwable, O]) {

    type F[A] = RIO[R, A]

    def none: Option[O] = None

    def toKorolev(bufferSize: Int = 1)(implicit ec: ExecutionContext, eff: KorolevEffect[F]): F[KorolevStream[F, O]] = {

      val queue = new Queue[RIO[R, *], O](bufferSize)
      val cancelToken: Either[Throwable, Unit] = Right(())

      KorolevEffect[F]
        .start(
          stream
            .interruptWhen(queue.cancelSignal.as(cancelToken))
            .tap(o => queue.enqueue(o))
            .runDrain
            .zipRight(queue.stop())
        )
        .as(queue.stream)
    }
  }



}
