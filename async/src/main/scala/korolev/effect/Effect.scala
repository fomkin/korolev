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

package korolev.effect

import scala.annotation.implicitNotFound
import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, TimeoutException}
import scala.util.Try

@implicitNotFound("Instance of Effect for ${F} is not found. If you want Future, ensure that execution context is passed to the scope (import korolev.execution.defaultExecutor)")
trait Effect[F[_]] {
  def pure[A](value: A): F[A]
  def delay[A](value: => A): F[A]
  def fork[A](value: => A): F[A]
  def unit: F[Unit]
  def fromTry[A](value: => Try[A]): F[A]
  def promise[A]: Effect.Promise[F, A]
  def flatMap[A, B](m: F[A])(f: A => F[B]): F[B]
  def map[A, B](m: F[A])(f: A => B): F[B]
  def recover[A](m: F[A])(f: PartialFunction[Throwable, A]): F[A]
  def sequence[A](in: List[F[A]]): F[List[A]]
  def runAsync[A, U](m: F[A])(callback: Try[A] => U): Unit
  def run[A](m: F[A], timeout: Duration): Option[A]
  def toFuture[A](m: F[A]): Future[A]
}

object Effect {

  private val futureInstanceCache =
    mutable.Map.empty[ExecutionContext, Effect[Future]]

  trait Promise[F[_], A] {
    def effect: F[A]
    def complete(`try`: Try[A]): Unit
    def completeAsync(async: F[A]): Unit
  }

  def apply[F[_]: Effect]: Effect[F] = implicitly[Effect[F]]

  private final class FutureEffect(implicit ec: ExecutionContext) extends Effect[Future] {
    val unit: Future[Unit] = Future.successful(())
    def toFuture[A](m: Future[A]): Future[A] = m
    def pure[A](value: A): Future[A] = Future.successful(value)
    def delay[A](value: => A): Future[A] = Future.successful(value)
    def fork[A](value: => A): Future[A] = Future(value)
    def fromTry[A](value: => Try[A]): Future[A] = Future.fromTry(value)
    def flatMap[A, B](m: Future[A])(f: A => Future[B]): Future[B] = m.flatMap(f)
    def map[A, B](m: Future[A])(f: A => B): Future[B] = m.map(f)
    def runAsync[A, U](m: Future[A])(f: Try[A] => U): Unit = m.onComplete(f)
    def run[A](m: Future[A], timeout: Duration): Option[A] = try {
      Some(Await.result(m, timeout))
    } catch {
      case _: TimeoutException => None
    }
    def recover[A](m: Future[A])(f: PartialFunction[Throwable, A]): Future[A] = m.recover(f)
    def sequence[A](in: List[Future[A]]): Future[List[A]] =
      Future.sequence(in)
    def promise[A]: Promise[Future, A] = {
      val promise = scala.concurrent.Promise[A]()
      new Promise[Future, A] {
        val effect: Future[A] = promise.future
        def complete(`try`: Try[A]): Unit = {
          promise.complete(`try`)
          ()
        }

        def completeAsync(async: Future[A]): Unit = {
          promise.completeWith(async)
          ()
        }
      }
    }
  }

  /**
    * Creates an Async instance for Future type.
    * Physically one instance per execution context is maintained.
    */
  implicit def futureAsync(implicit ec: ExecutionContext): Effect[Future] = {
    futureInstanceCache.synchronized {
      futureInstanceCache.getOrElseUpdate(ec, new FutureEffect())
    }
  }
}
