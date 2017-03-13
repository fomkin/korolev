package korolev

import korolev.async.Async
import korolev.async.Async.Promise
import monix.eval.Task
import monix.execution.Scheduler

import scala.util.Try

package object async_monix {
  implicit def taskAsync(implicit s: Scheduler): Async[Task] = new Async[Task] {
    override def flatMap[A, B](m: Task[A])(f: (A) => Task[B]): Task[B] = m flatMap f
    override def promise[A]: Promise[Task, A] = {
      val promise = scala.concurrent.Promise[A]()
      Promise(Task.fromFuture(promise.future), promise.complete)
    }
    override def recover[A, U >: A](m: Task[A])(f: PartialFunction[Throwable, U]): Task[U] = m onErrorRecover f
    override def sequence[A](xs: Seq[Task[A]]): Task[Seq[A]] = Task.sequence(xs)
    override def run[A, U](m: Task[A])(f: (Try[A]) => U): Unit = m runOnComplete f.andThen(_ => ())
    override def fromTry[A](value: =>Try[A]): Task[A] = Task.fromTry(value)
    override def pure[A](value: => A): Task[A] = Task(value)
    override def unit: Task[Unit] = Task.unit
    override def map[A, B](m: Task[A])(f: (A) => B): Task[B] = m map f
  }
}
