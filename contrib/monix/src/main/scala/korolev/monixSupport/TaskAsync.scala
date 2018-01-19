package korolev.monixSupport

import korolev.Async
import monix.eval.Task
import monix.execution.Scheduler

import scala.collection.generic.CanBuildFrom
import scala.util.Try

private[monixSupport] final class TaskAsync(implicit scheduler: Scheduler) extends Async[Task] {
  override val unit: Task[Unit] = Task.unit
  override def pureStrict[A](value: A): Task[A] = Task.now(value)
  override def pure[A](value: => A): Task[A] = Task.now(value)
  override def fork[A](value: => A): Task[A] = Task(value)
  override def fromTry[A](value: => Try[A]): Task[A] = Task.fromTry(value)
  override def promise[A]: korolev.Async.Promise[Task, A] = {
    val promise = scala.concurrent.Promise[A]()
    korolev.Async.Promise(Task.fromFuture(promise.future), a => { promise.complete(a); () })
  }
  override def flatMap[A, B](m: Task[A])(f: A => Task[B]): Task[B] = m.flatMap(f)
  override def map[A, B](m: Task[A])(f: A => B): Task[B] = m.map(f)
  override def recover[A, U >: A](m: Task[A])(f: PartialFunction[Throwable, U]): Task[U] = m.onErrorRecover(f)
  override def sequence[A, M[X] <: TraversableOnce[X]](in: M[Task[A]])
                                                      (implicit cbf: CanBuildFrom[M[Task[A]], A, M[A]]): Task[M[A]] =
    Task.sequence(in)
  override def run[A, U](m: Task[A])(f: Try[A] => U): Unit = { m.runOnComplete(r => { f(r); () }); () }
}
