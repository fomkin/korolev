package korolev.http4s

import cats.effect.{ConcurrentEffect, IO}
import cats.effect.implicits._

object EffectRunner {

  private[http4s] implicit class Runner[F[_]: ConcurrentEffect, A](fa: F[A]) {
    def reportFailure(e: Throwable) =
      Thread.getDefaultUncaughtExceptionHandler match {
        case null => e.printStackTrace()
        case h    => h.uncaughtException(Thread.currentThread(), e)
      }

    def unsafeRunAsync(): Unit =
      fa.runAsync {
        case Left(e)  => IO(reportFailure(e))
        case Right(_) => IO.unit
      }.unsafeRunSync()

    def unsafeRunSync(): Unit = {
      val io = ConcurrentEffect[F].toIO(fa)
      io.attempt.unsafeRunSync() match {
        case Left(failure) => reportFailure(failure)
        case _             =>
      }
    }
  }

}
