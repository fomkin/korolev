package korolev

import scala.util.{Failure, Success, Try}
import scalaz.{-\/, \/, \/-}
import scalaz.concurrent.Task

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
object scalazSupport {
  implicit def TaskAsync: Async[Task] = {
    new Async[Task] {
      val unit: Task[Unit] = Task.now(())
      def pure[A](value: => A): Task[A] = Task.now(value)
      def fromTry[A](value: => Try[A]): Task[A] = value match {
        case Success(x) => Task.now(x)
        case Failure(e) => Task.fail(e)
      }
      def flatMap[A, B](m: Task[A])(f: (A) => Task[B]): Task[B] = m.flatMap(f)
      def map[A, B](m: Task[A])(f: (A) => B): Task[B] = m.map(f)
      def run[A, U](m: Task[A])(f: (Try[A]) => U): Unit = m runAsync {
        case \/-(x) => f(Success(x))
        case -\/(e) => f(Failure(e))
      }
      def promise[A]: Async.Promise[Task, A] = {
        var maybeValue = Option.empty[Try[A]]
        var maybeCallback = Option.empty[Try[A] => Unit]
        val task = Task async { (f: \/[Throwable, A] => Unit) =>
          val callback: Try[A] => Unit = {
            case Success(x) => f(\/-(x))
            case Failure(e) => f(-\/(e))
          }
          maybeCallback = Some(callback)
          maybeValue.foreach(callback)
        }
        val onComplete: Try[A] => Unit = (tryValue) => maybeCallback match {
          case Some(callback) => callback(tryValue)
          case None => maybeValue = Some(tryValue)
        }
        Async.Promise(task, onComplete)
      }
    }
  }
}
