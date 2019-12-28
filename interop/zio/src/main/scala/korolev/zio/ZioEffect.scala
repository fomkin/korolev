package korolev.zio

import _root_.zio.{Exit, FiberFailure, IO, RIO, Runtime, Task, ZIO}
import korolev.effect.Effect

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class ZioEffect[R, E](rts: Runtime[R],
                      liftError: Throwable => E,
                      unliftError: E => Throwable) extends Effect[ZIO[R, E, *]] {

  private val unliftErrorP: PartialFunction[E, Throwable] =
    { case x => unliftError(x) }

  def pure[A](value: A): ZIO[R, E, A] =
    ZIO.succeed(value)

  def delay[A](value: => A): ZIO[R, E, A] =
    IO.effect(value).mapError(liftError)

  def fail[A](e: Throwable): ZIO[R, E, A] =
    Task.fail(e).mapError(liftError)

  def unit: ZIO[R, E, Unit] =
    ZIO.unit

  def fromTry[A](value: => Try[A]): ZIO[R, E, A] =
    Task.fromTry(value).mapError(liftError)

  def promise[A](k: (Either[Throwable, A] => Unit) => Unit): ZIO[R, E, A] =
    ZIO.effectAsync(kk => k(kk apply _.fold(liftError.andThen(ZIO.fail), ZIO.succeed)))

  def promiseF[A](k: (Either[Throwable, A] => Unit) => ZIO[R, E, Unit]): ZIO[R, E, A] = {
    ZIO.effectAsyncM(kk => k(kk apply _.fold(liftError.andThen(ZIO.fail), ZIO.succeed)))
  }

  def flatMap[A, B](m: ZIO[R, E, A])(f: A => ZIO[R, E, B]): ZIO[R, E, B] =
    m.flatMap(f)

  def map[A, B](m: ZIO[R, E, A])(f: A => B): ZIO[R, E, B] =
    m.map(f)

  def recover[A](m: ZIO[R, E, A])(f: PartialFunction[Throwable, A]): ZIO[R, E, A] = {
    m.catchSome(unliftErrorP.andThen(f).andThen(ZIO.succeed _))
  }

  def start[A](task: => ZIO[R, E, A])(implicit ec: ExecutionContext): ZIO[R, E, Effect.Fiber[ZIO[R, E, *], A]] =
    RIO
      .interruptible(task.mapError(unliftError))
      .nonDaemon
      .fork
      .daemon
      .map { fiber =>
        () => fiber.join.mapError(liftError)
      }

  def fork[A](m: => ZIO[R, E, A])(implicit ec: ExecutionContext): ZIO[R, E, A] =
    m.on(ec)

  def sequence[A](in: List[ZIO[R, E, A]]): ZIO[R, E, List[A]] =
    ZIO.sequence(in)

  def runAsync[A](m: ZIO[R, E, A])(callback: Either[Throwable, A] => Unit): Unit =
    rts.unsafeRunAsync(m)(exit => callback(exit.toEither))

  def run[A](m: ZIO[R, E, A]): A =
    rts.unsafeRunSync(m) match {
      case Exit.Success(value) => value
      case Exit.Failure(cause) => throw FiberFailure(cause)
    }

  def toFuture[A](m: ZIO[R, E, A]): Future[A] =
    rts.unsafeRunToFuture(m.mapError(unliftError))
}
