package korolev

import scala.annotation.implicitNotFound
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@implicitNotFound("Instance of Async for ${F} is not found. If you want Future, ensure that execution context is passed to the scope (import korolev.execution.defaultExecutor)")
trait Async[F[+_]] {
  def pureStrict[A](value: A): F[A]
  def pure[A](value: => A): F[A]
  def fork[A](value: => A): F[A]
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

  private val futureInstanceCache =
    mutable.Map.empty[ExecutionContext, Async[Future]]

  case class Promise[F[+_], A](future: F[A], complete: Try[A] => Unit)

  def apply[F[+_]: Async]: Async[F] = implicitly[Async[F]]

  private final class FutureAsync(implicit ec: ExecutionContext) extends Async[Future] {
    val unit: Future[Unit] = Future.successful(())
    def pureStrict[A](value: A): Future[A] = Future.successful(value)
    def pure[A](value: => A): Future[A] = Future.successful(value)
    def fork[A](value: => A): Future[A] = Future(value)
    def fromTry[A](value: => Try[A]): Future[A] = Future.fromTry(value)
    def flatMap[A, B](m: Future[A])(f: (A) => Future[B]): Future[B] = m.flatMap(f)
    def map[A, B](m: Future[A])(f: (A) => B): Future[B] = m.map(f)
    def run[A, U](m: Future[A])(f: (Try[A]) => U): Unit = m.onComplete(f)
    def recover[A, U >: A](m: Future[A])(f: PartialFunction[Throwable, U]): Future[U] = m.recover(f)
    def sequence[A](xs: Seq[Future[A]]): Future[Seq[A]] = Future.sequence(xs)
    def promise[A]: Promise[Future, A] = {
      val promise = scala.concurrent.Promise[A]()
      Promise(promise.future, a => { promise.complete(a); () })
    }
  }

  /**
    * Gives an instance of Async for Future type.
    * Physically one instance for every execution context
    */
  implicit def futureAsync(implicit ec: ExecutionContext): Async[Future] = {
    futureInstanceCache.synchronized {
      futureInstanceCache.getOrElseUpdate(ec, new FutureAsync())
    }
  }

  implicit final class AsyncOps[F[+_]: Async, +A](async: => F[A]) {
    def map[B](f: A => B): F[B] = Async[F].map(async)(f)
    def flatMap[B](f: A => F[B]): F[B] = Async[F].flatMap(async)(f)
    def recover[U >: A](f: PartialFunction[Throwable, U]): F[U] = Async[F].recover[A, U](async)(f)
    def run[U](f: Try[A] => U): Unit = Async[F].run(async)(f)
    def runIgnoreResult(implicit er: ErrorReporter = ErrorReporter.default): Unit =
      Async[F].run(async) {
        case Success(_) => // do nothing
        case Failure(e) => er.reportError(e)
      }
  }

  trait ErrorReporter {
    def reportError(error: Throwable): Unit
  }

  object ErrorReporter {
    def apply(f: Throwable => Unit): ErrorReporter = new ErrorReporter {
      def reportError(error: Throwable): Unit = f(error)
    }
    val default = new ErrorReporter {
      def reportError(error: Throwable): Unit = {
        error.printStackTrace()
      }
    }
  }
}
