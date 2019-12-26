package korolev.zio

import _root_.zio.{Exit, FiberFailure, RIO, Runtime, Task, ZIO}
import korolev.effect.Effect

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class ZioEffect[R](rts: Runtime[R]) extends Effect[Task] {

  def pure[A](value: A): Task[A] =
    ZIO.succeed(value)

  def delay[A](value: => A): Task[A] =
    Task.effect(value)

  def fail[A](e: Throwable): Task[A] =
    Task.fail(e)

  def unit: Task[Unit] =
    ZIO.unit

  def fromTry[A](value: => Try[A]): Task[A] =
    Task.fromTry(value)

  def promise[A](k: (Either[Throwable, A] => Unit) => Unit): Task[A] =
    RIO.effectAsync(kk => k(kk apply _.fold(ZIO.fail, ZIO.succeed)))

  def promiseF[A](k: (Either[Throwable, A] => Unit) => Task[Unit]): Task[A] =
    RIO.effectAsyncM(kk => k(kk apply _.fold(ZIO.fail, ZIO.succeed)).orDie)

  def flatMap[A, B](m: Task[A])(f: A => Task[B]): Task[B] =
    m.flatMap(f)

  def map[A, B](m: Task[A])(f: A => B): Task[B] =
    m.map(f)

  def recover[A](m: Task[A])(f: PartialFunction[Throwable, A]): Task[A] =
    m.catchSome(f.andThen(ZIO.succeed))

  def start[A](task: => Task[A])(implicit ec: ExecutionContext): Task[Effect.Fiber[Task, A]] =
    RIO
      .interruptible(task)
      .nonDaemon
      .fork
      .daemon
      .map { fiber =>
        () => fiber.join
      }

  def fork[A](m: => Task[A])(implicit ec: ExecutionContext): Task[A] =
    m.on(ec)

  def sequence[A](in: List[Task[A]]): Task[List[A]] =
    ZIO.sequence(in)

  def runAsync[A](m: Task[A])(callback: Either[Throwable, A] => Unit): Unit =
    rts.unsafeRunAsync(m)(exit => callback(exit.toEither))

  def run[A](m: Task[A]): A =
    rts.unsafeRunSync(m) match {
      case Exit.Success(value) => value
      case Exit.Failure(cause) => throw FiberFailure(cause)
    }

  def toFuture[A](m: Task[A]): Future[A] =
    rts.unsafeRunToFuture(m)
}
