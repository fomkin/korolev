package korolev.zio

import korolev.effect.{Effect as KorolevEffect, Stream as KorolevStream}
import zio.stream.ZStream
import zio._

package object streams {

  implicit class KorolevStreamOps[R, O](stream: KorolevStream[RIO[R, *], O]) {

    def toZStream: ZStream[R, Throwable, O] = {
      ZStream.unfoldZIO(()) { _ =>
        stream
          .pull()
          .map(mv => mv.map(v => (v, ())))
      }
    }
  }

  private type Finalizer = Exit[Any, Any] => UIO[Any]

  implicit class ZStreamOps[R, O](stream: ZStream[R, Throwable, O]) {

    def toKorolev(implicit eff: KorolevEffect[RIO[R, *]]): RIO[R, ZKorolevStream[R, O]] = {
      Ref.make(List.empty[Finalizer]).flatMap { finalizersRef =>
        val scope = new Scope {
          def addFinalizerExit(finalizer: Finalizer)(implicit trace: Trace): UIO[Unit] =
            finalizersRef.update(finalizer :: _)
          def forkWith(executionStrategy: => ExecutionStrategy)(implicit trace: Trace): UIO[Scope.Closeable] =
            ZIO.dieMessage("Can't fork ZKorolevStream")
        }
        (for {
          pull <- stream.toPull
          zStream = ZKorolevStream[R, O](pull, finalizersRef)
        } yield zStream).provideSomeLayer[R](ZLayer.succeed(scope))
      }
    }
  }

  private[streams] case class ZKorolevStream[R, O]
    (zPull: ZIO[R, Option[Throwable], Chunk[O]],
     finalizersRef: Ref[List[Finalizer]]
    )(implicit eff: KorolevEffect[RIO[R, *]]) extends KorolevStream[RIO[R, *], Seq[O]] {

    def pull(): RIO[R, Option[Seq[O]]] =
      zPull.option

    def cancel(): RIO[R, Unit] =
      for {
        finalizers <- finalizersRef.get
        _ <- ZIO.collectAll(finalizers.map(_(Exit.unit))).unit
      } yield ()
  }
}
