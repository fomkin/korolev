/*
 * Copyright 2017-2020 Aleksey Fomkin
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

import korolev.effect.Effect.Fiber

import scala.annotation.implicitNotFound
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * Korolev's internal presentation of effect (such as Future, cats.effect.IO, Monix or ZIO tasks).
  * Contains enough functionality to make Korolev works.
  */
@implicitNotFound("Instance of Effect for ${F} is not found.")
trait Effect[F[_]] {
  def pure[A](value: A): F[A]
  def delay[A](value: => A): F[A]
  def fail[A](e: Throwable): F[A]
  def unit: F[Unit]
  def fromTry[A](value: Try[A]): F[A]
  def promise[A](cb: (Either[Throwable, A] => Unit) => Unit): F[A]
  def promiseF[A](cb: (Either[Throwable, A] => Unit) => F[Unit]): F[A]
  def flatMap[A, B](m: F[A])(f: A => F[B]): F[B]
  def map[A, B](m: F[A])(f: A => B): F[B]
  def recover[A](m: F[A])(f: PartialFunction[Throwable, A]): F[A]
  /** Keep in mind that when [[F]] has strict semantic, effect should
    * created inside 'start()' brackets. */
  def start[A](create: => F[A])(implicit ec: ExecutionContext): F[Fiber[F, A]]
  /** Keep in mind that when [[F]] has strict semantic, effect should
    * created inside 'fork()' brackets. */
  def fork[A](m: => F[A])(implicit ec: ExecutionContext): F[A]
  def sequence[A](in: List[F[A]]): F[List[A]]
  def runAsync[A](m: F[A])(callback: Either[Throwable, A] => Unit): Unit
  def run[A](m: F[A]): A
  def toFuture[A](m: F[A]): Future[A]
}

object Effect {

  type Promise[A] = Either[Throwable, A] => Unit

  trait Fiber[F[_], A] {
    def join(): F[A]
  }

  def apply[F[_]: Effect]: Effect[F] = implicitly[Effect[F]]

  final class FutureEffect extends Effect[Future] {

    val unit: Future[Unit] = BrightFuture.unit
    def toFuture[A](m: Future[A]): Future[A] = m
    def fail[A](e: Throwable): Future[A] = BrightFuture.Error(e)
    def pure[A](value: A): Future[A] = BrightFuture.Pure(value)
    def delay[A](value: => A): Future[A] = BrightFuture.Delay(() => value)
    def fork[A](m: => Future[A])(implicit ec: ExecutionContext): Future[A] =
      BrightFuture.Map[A, A](m, identity, ec)
    def fromTry[A](value: Try[A]): Future[A] = value match {
      case Success(value) => BrightFuture.Pure(value)
      case Failure(exception) => BrightFuture.Error(exception)
    }
    def flatMap[A, B](m: Future[A])(f: A => Future[B]): Future[B] = m.flatMap(f)(RunNowExecutionContext)
    def map[A, B](m: Future[A])(f: A => B): Future[B] = m.map(f)(RunNowExecutionContext)
    def runAsync[A](m: Future[A])(f: Either[Throwable, A] => Unit): Unit =
      m match {
        case bf: BrightFuture[A] => bf.run(RunNowExecutionContext, stateless = true)(x => f(x.toEither))
        case _ => m.onComplete(x => f(x.toEither))(RunNowExecutionContext)
      }

    def run[A](m: Future[A]): A =
      Await.result(m, Duration.Inf)
    def recover[A](m: Future[A])(f: PartialFunction[Throwable, A]): Future[A] = m.recover(f)(RunNowExecutionContext)
    def sequence[A](in: List[Future[A]]): Future[List[A]] = {
      implicit val executor: ExecutionContext = RunNowExecutionContext
      Future.sequence(in)
    }
    def start[A](create: => Future[A])(implicit ec: ExecutionContext): Future[Fiber[Future, A]] = {
      val f = Future(create)(ec).flatten
      Future.successful(() => f) // TODO ???
    }
    def promise[A](cb: (Either[Throwable, A] => Unit) => Unit): Future[A] = {
      BrightFuture.Async(cb)
    }
    def promiseF[A](cb: (Either[Throwable, A] => Unit) => Future[Unit]): Future[A] = {
      val promise = scala.concurrent.Promise[A]()
      cb(or => promise.complete(or.toTry)).flatMap { _ =>
        promise.future
      }(RunNowExecutionContext) // TODO ???
    }
  }

  implicit val futureEffect: Effect[Future] =
    new FutureEffect()
}
