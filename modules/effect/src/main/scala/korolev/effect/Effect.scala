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
  private val noneVal: F[None.type] = pure(None)
  def none[A]: F[Option[A]] = noneVal.asInstanceOf[F[Option[A]]]
  def pure[A](value: A): F[A]
  def delay[A](value: => A): F[A]
  def delayAsync[A](value: => F[A]): F[A] = flatMap(delay(value))(identity)
  def fail[A](e: Throwable): F[A]
  def unit: F[Unit]
  def never[T]: F[T]
  def fromTry[A](value: => Try[A]): F[A]
  def promise[A](cb: (Either[Throwable, A] => Unit) => Unit): F[A]
  def promiseF[A](cb: (Either[Throwable, A] => Unit) => F[Unit]): F[A]
  def flatMap[A, B](m: F[A])(f: A => F[B]): F[B]
  def map[A, B](m: F[A])(f: A => B): F[B]
  def recover[A, AA >: A](m: F[A])(f: PartialFunction[Throwable, AA]): F[AA]
  def recoverF[A, AA >: A](m: F[A])(f: PartialFunction[Throwable, F[AA]]): F[AA]
//  def onError[A](m: F[A])(f: Throwable => Unit): F[A]
//  def onErrorF[A](m: F[A])(f: Throwable => F[Unit]): F[A]
  /** Keep in mind that when [[F]] has strict semantic, effect should
    * created inside 'start()' brackets. */
  def start[A](create: => F[A])(implicit ec: ExecutionContext): F[Fiber[F, A]]
  /** Keep in mind that when [[F]] has strict semantic, effect should
    * created inside 'fork()' brackets. */
  def fork[A](m: => F[A])(implicit ec: ExecutionContext): F[A]
  def sequence[A](in: List[F[A]]): F[List[A]]
  def runAsync[A](m: F[A])(callback: Either[Throwable, A] => Unit): Unit
  def run[A](m: F[A]): Either[Throwable, A]
  def toFuture[A](m: F[A]): Future[A]
}

object Effect {

  type Promise[A] = Either[Throwable, A] => Unit

  trait Fiber[F[_], A] {
    def join(): F[A]
  }

  def apply[F[_]: Effect]: Effect[F] = implicitly[Effect[F]]

  class FutureEffect extends Effect[Future] {
    private implicit val immediateEc: ExecutionContext = new ExecutionContext {
      // Run on the same thread
      def execute(runnable: Runnable): Unit = runnable.run()
      def reportFailure(cause: Throwable): Unit = cause.printStackTrace()
    }
    val unit: Future[Unit] = Future.unit
    def never[T]: Future[T] = Future.never
    def toFuture[A](m: Future[A]): Future[A] = m
    def fail[A](e: Throwable): Future[A] = Future.failed(e)
    def pure[A](value: A): Future[A] = Future.successful(value)
    def delay[A](value: => A): Future[A] =
      try {
        Future.successful(value)
      } catch {
        case error: Throwable =>
          Future.failed(error)
      }
    def fork[A](m: => Future[A])(implicit ec: ExecutionContext): Future[A] =
      Future(m)(ec).flatten
    def fromTry[A](value: => Try[A]): Future[A] = Future.fromTry(value)
    def flatMap[A, B](m: Future[A])(f: A => Future[B]): Future[B] = m.flatMap(f)
    def map[A, B](m: Future[A])(f: A => B): Future[B] = m.map(f)
    def runAsync[A](m: Future[A])(f: Either[Throwable, A] => Unit): Unit =
      m.onComplete(x => f(x.toEither))
    def run[A](m: Future[A]): Either[Throwable, A] =
      Try(Await.result(m, Duration.Inf)).toEither
    def recover[A, AA >: A](m: Future[A])(f: PartialFunction[Throwable, AA]): Future[AA] = m.recover(f)
    def recoverF[A, AA >: A](m: Future[A])(f: PartialFunction[Throwable, Future[AA]]): Future[AA] = m.recoverWith(f)
//    def onError[A](m: Future[A])(f: Throwable => Unit): Future[A] = {
//      m.onComplete {
//        case Success(value) => ()
//        case Failure(exception) =>
//          f(exception)
//      }
//      m
//    }
//    def onErrorF[A](m: Future[A])(f: Throwable => Future[Unit]): Future[A] = {
//      m.onComplete {
//        case Success(value) => ()
//        case Failure(exception) =>
//          f(exception)
//      }
//      m
//    }
    /** Keep in mind that when [[F]] has strict semantic, effect should
     * created inside 'start()' brackets. */
    def sequence[A](in: List[Future[A]]): Future[List[A]] =
      Future.sequence(in)
    def start[A](create: => Future[A])(implicit ec: ExecutionContext): Future[Fiber[Future, A]] = {
      val f = Future(create)(ec).flatten
      Future.successful {
        new Fiber[Future, A] {
          override def join(): Future[A] = f
        }
      }
    }
    def promise[A](cb: (Either[Throwable, A] => Unit) => Unit): Future[A] = {
      val promise = scala.concurrent.Promise[A]()
      try {
        cb(or => promise.complete(or.toTry))
        promise.future
      } catch {
        case e: Throwable =>
          Future.failed(e)
      }
    }
    def promiseF[A](cb: (Either[Throwable, A] => Unit) => Future[Unit]): Future[A] = {
      val promise = scala.concurrent.Promise[A]()
      // FIXME handle error
      cb(or => promise.complete(or.toTry)).flatMap { _ =>
        promise.future
      }
    }
  }

  implicit val futureEffect: Effect[Future] =
    new FutureEffect()
}
