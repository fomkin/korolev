package korolev.zio

import korolev.effect.{Queue, Effect => KorolevEffect, Stream => KorolevStream}
import korolev.effect.syntax._
import zio.stream.{Take, ZStream}
import zio.{RIO, ZIO, ZManaged, Queue => ZQueue}
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal


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

    type F = RIO[R, *]

    def toKorolev(bufferSize: Int = 1)(implicit ec: ExecutionContext, eff: KorolevEffect[F]): KorolevStream[F, O] = {

      val queue = new Queue[F, O](bufferSize)
      val cancelToken: Either[Throwable, Unit] = Right(())

      KorolevEffect[F]
        .start(
          stream
           // .interruptWhen(queue.cancelSignal.as(cancelToken))
            .flatMap(o => queue.join.flatMap(_ => queue.offer(o)))
            .compile
            .drain
            .flatMap(_ => queue.stop())
        )
        .as(queue.stream)
    }
  }



}
