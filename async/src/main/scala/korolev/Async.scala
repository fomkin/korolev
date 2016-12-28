package korolev

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.Try

trait Async[F[+_]] {
  def pure[A](value: => A): F[A]
  def unit: F[Unit]
  def fromTry[A](value: => Try[A]): F[A]
  def fork[A](value: => A): F[A]
  def promise[A]: Async.Promise[F, A]
  def flatMap[A, B](m: F[A])(f: A => F[B]): F[B]
  def map[A, B](m: F[A])(f: A => B): F[B]
  def run[A, U](m: F[A])(f: Try[A] => U): Unit
}

object Async {

  case class Promise[F[+_], A](future: F[A], complete: Try[A] => Unit)

  def apply[F[+_]: Async]: Async[F] = implicitly[Async[F]]

  implicit def futureAsync(implicit ec: ExecutionContext): Async[Future] = {
    new Async[Future] {
      val unit: Future[Unit] = Future.successful(())
      def pure[A](value: => A): Future[A] = Future.successful(value)
      def fromTry[A](value: => Try[A]): Future[A] = Future.fromTry(value)
      def fork[A](value: => A): Future[A] = Future(value)
      def flatMap[A, B](m: Future[A])(f: (A) => Future[B]): Future[B] = m.flatMap(f)
      def map[A, B](m: Future[A])(f: (A) => B): Future[B] = m.map(f)
      def run[A, U](m: Future[A])(f: (Try[A]) => U): Unit = m.onComplete(f)
      def promise[A]: Promise[Future, A] = {
        val promise = scala.concurrent.Promise[A]()
        Promise(promise.future, promise.complete)
      }
    }
  }
}
