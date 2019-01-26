/*
 * Copyright 2017-2018 Aleksey Fomkin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package korolev.monixSupport

import korolev.Async
import korolev.Async.Promise
import monix.eval.Task
import monix.execution.{Callback, Scheduler}

import scala.collection.generic.CanBuildFrom
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

private[monixSupport] final class TaskAsync(implicit scheduler: Scheduler) extends Async[Task] {
  override val unit: Task[Unit] = Task.unit
  override def pureStrict[A](value: A): Task[A] = Task.now(value)
  override def pure[A](value: => A): Task[A] = Task.now(value)
  override def fork[A](value: => A): Task[A] = Task(value)
  override def fromTry[A](value: => Try[A]): Task[A] = Task.fromTry(value)
  override def promise[A]: korolev.Async.Promise[Task, A] = {
    val promise = scala.concurrent.Promise[A]()
    new Promise[Task, A] {
      val async: Task[A] = Task.fromFuture(promise.future)
      def complete(`try`: Try[A]): Unit = promise.complete(`try`)
      def completeAsync(async: Task[A]): Unit = promise.completeWith(async.runToFuture)
    }
  }
  override def flatMap[A, B](m: Task[A])(f: A => Task[B]): Task[B] = m.flatMap(f)
  override def map[A, B](m: Task[A])(f: A => B): Task[B] = m.map(f)
  override def recover[A, U >: A](m: Task[A])(f: PartialFunction[Throwable, U]): Task[U] = m.onErrorRecover(f)
  override def sequence[A, M[X] <: TraversableOnce[X]](in: M[Task[A]])
                                                      (implicit cbf: CanBuildFrom[M[Task[A]], A, M[A]]): Task[M[A]] =
    Task.sequence(in)
  override def run[A, U](m: Task[A])(f: Try[A] => U): Unit = {
    m.runAsync { r => { f(r.fold(Failure(_), Success(_))); () } }
    ()
  }
}
