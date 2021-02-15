package korolev.zio

import korolev.effect.{Effect => KorolevEffect, Stream => KorolevStream}
import korolev.effect.syntax._
import zio.stream.ZStream
import zio.{Chunk, Exit, FiberFailure, RIO, Runtime, ZIO, ZManaged}

import scala.annotation.tailrec
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

    def toKorolev(implicit ec: ExecutionContext, eff: KorolevEffect[F]): F[KorolevStream[F, O]] = {
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
      pull: ZIO[R, Option[Throwable], Chunk[O]]
    )(implicit eff: KorolevEffect[RIO[R, *]]) extends KorolevStream[RIO[R, *], O] {

    type F[A] = RIO[R, A]

    private var current: Chunk[O] = Chunk.empty
    private var currentPos: Int      = 0
    private var currentChunkLen: Int = 0
    private var done: Boolean        = false

    @inline private def availableInCurrentChunk: Int = currentChunkLen - currentPos

    private def loadNext(): Unit =
      if (chunks.hasNext) {
        current = chunks.next()
        currentChunkLen = current.length
        currentPos = 0
      } else {
        done = true
      }

    def unfoldPull: Iterator[Chunk[O]] = {
      runtime.unsafeRunSync(pull) match {
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

    @inline
    private def readOne(): O = {
      val res = current(currentPos)
      currentPos += 1
      res
    }

    def pull(): F[Option[O]] = ZIO {
      @tailrec
      def go(): Option[O] =
        if (done) {
          None
        } else {
          if (availableInCurrentChunk > 0) {
            Some(readOne())
          } else {
            loadNext()
            go()
          }
        }
      go()
    }

    def cancel(): F[Unit] = ZIO {
      chunks = Iterator.empty
      loadNext()
    }
  }


}
