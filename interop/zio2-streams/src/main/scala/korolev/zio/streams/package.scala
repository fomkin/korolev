package korolev.zio

import korolev.effect.{Effect as KorolevEffect, Stream as KorolevStream}
import zio.stream.ZStream
import zio.{Chunk, RIO, Runtime, Scope, ZIO}


package object streams {


  implicit class KorolevSreamOps[R, O](stream: KorolevStream[RIO[R, *], O]) {

    def toZStream: ZStream[R, Throwable, O] = {
      ZStream.unfoldZIO(()) { _ =>
        stream
          .pull()
          .map(mv => mv.map(v => (v, ())))
      }
    }
  }

  implicit class ZStreamOps[R, O](stream: ZStream[R, Throwable, O]) {

    type F[A] = RIO[R, A]

    def toKorolev(implicit eff: KorolevEffect[F]): ZIO[R with Scope, Nothing, ZKorolevStream[R, O]] = {
      for {
        runtime <- ZIO.runtime[R]
        pull <- stream.toPull
      } yield new ZKorolevStream(runtime, pull)
    }
  }

  private[streams] class ZKorolevStream[R, O]
    (
      runtime: Runtime[R],
      zPull: ZIO[R, Option[Throwable], Chunk[O]]
    )(implicit eff: KorolevEffect[RIO[R, *]]) extends KorolevStream[RIO[R, *], Seq[O]] {

    def pull(): RIO[R, Option[Seq[O]]] =
      zPull.option

    def cancel(): RIO[R, Unit] =
      ZIO.dieMessage("Can't cancel ZStream from Korolev")
  }
}
