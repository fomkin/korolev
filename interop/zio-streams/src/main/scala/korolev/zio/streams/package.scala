package korolev.zio

import korolev.effect.{Effect => KorolevEffect, Stream => KorolevStream}
import zio.stream.ZStream
import zio.{Chunk, Exit, FiberFailure, RIO, Runtime, ZIO}
import scala.concurrent.ExecutionContext


package object streams {


  implicit class KorolevtSreamOps[R, O](stream: KorolevStream[RIO[R, *], O]) {

    def toZStream: ZStream[R, Throwable, O] = {
      ZStream.unfoldM(()) { _ =>
        stream
          .pull()
          .map(mv => mv.map(v => (v, ())))
      }
    }
  }

  implicit class ZStreamOps[R, O](stream: ZStream[R, Throwable, O]) {

    type F[A] = RIO[R, A]

    def toKorolev(implicit eff: KorolevEffect[F]): F[KorolevStream[F, Seq[O]]] = {
      (for {
        runtime <- ZIO.runtime[R].toManaged_
        pull <- stream.process
      } yield {
        new ZKorolevStream(runtime, pull)
      }).useNow
    }
  }

  private[streams] class ZKorolevStream[R, O]
    (
      runtime: Runtime[R],
      zPull: ZIO[R, Option[Throwable], Chunk[O]]
    )(implicit eff: KorolevEffect[RIO[R, *]]) extends KorolevStream[RIO[R, *], Seq[O]] {

    type F[A] = RIO[R, A]


    def unfoldPull: Iterator[Chunk[O]] = {
      runtime.unsafeRunSync(zPull) match {
        case Exit.Success(chunk) => Iterator.single(chunk) ++ unfoldPull
        case Exit.Failure(cause) =>
          cause.failureOrCause match {
            case Left(None)    => Iterator.empty
            case Left(Some(e)) => throw e
            case Right(c)      => throw FiberFailure(c)
          }
      }
    }

    var chunks = unfoldPull

    def pull(): F[Option[Seq[O]]] = ZIO {
      if (chunks.hasNext) {
        Some(chunks.next())
      } else {
        None
      }
    }

    def cancel(): F[Unit] = ZIO {
      chunks = Iterator.empty
    }
  }


}
