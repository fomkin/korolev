package korolev

import scala.annotation.implicitNotFound
import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.Try

@implicitNotFound("""Instance of Async for `${F}` is not found. If you want Future,
 ensure that execution context is passed to a scope (import korolev.blazeServer.defaultExecutor)""")
trait Async[F[+_]] {
  def pure[A](value: => A): F[A]
  def unit: F[Unit]
  def fromTry[A](value: => Try[A]): F[A]
  def promise[A]: Async.Promise[F, A]
  def flatMap[A, B](m: F[A])(f: A => F[B]): F[B]
  def map[A, B](m: F[A])(f: A => B): F[B]
  def recover[A, U >: A](m: F[A])(f: PartialFunction[Throwable, U]): F[U]
  def sequence[A](xs: Seq[F[A]]): F[Seq[A]]
  def run[A, U](m: F[A])(f: Try[A] => U): Unit
}

object Async {

  case class Promise[F[+_], A](future: F[A], complete: Try[A] => Unit)

  def apply[F[+_]: Async]: Async[F] = implicitly[Async[F]]

  implicit def futureAsync(implicit ec: ExecutionContext): Async[Future] = {
    new Async[Future] {
      val unit: Future[Unit] = Future.successful(())
      def pure[A](value: => A): Future[A] =
        try Future.successful(value)
        catch { case e: Throwable => Future.failed(e) }
      def fromTry[A](value: => Try[A]): Future[A] = Future.fromTry(value)
      def flatMap[A, B](m: Future[A])(f: (A) => Future[B]): Future[B] = m.flatMap(f)
      def map[A, B](m: Future[A])(f: (A) => B): Future[B] = m.map(f)
      def run[A, U](m: Future[A])(f: (Try[A]) => U): Unit = m.onComplete(f)
      def recover[A, U >: A](m: Future[A])(f: PartialFunction[Throwable, U]): Future[U] = m.recover(f)
      def sequence[A](xs: Seq[Future[A]]): Future[Seq[A]] = Future.sequence(xs)
      def promise[A]: Promise[Future, A] = {
        val promise = scala.concurrent.Promise[A]()
        Promise(promise.future, promise.complete)
      }
    }
  }

  implicit final class AsyncOps[F[+_]: Async, +A](async: => F[A]) {
    def map[B](f: A => B): F[B] = Async[F].map(async)(f)
    def flatMap[B](f: A => F[B]): F[B] = Async[F].flatMap(async)(f)
    def recover[U >: A](f: PartialFunction[Throwable, U]): F[U] = Async[F].recover[A, U](async)(f)
    def run[U](f: Try[A] => U): Unit = Async[F].run(async)(f)
    def run(): Unit = Async[F].run(async)(_ => ())
  }
}
