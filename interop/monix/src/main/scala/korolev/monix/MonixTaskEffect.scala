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

package korolev.monix

import korolev.effect.Effect
import _root_.monix.eval.Task
import _root_.monix.execution.Scheduler

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class MonixTaskEffect(implicit scheduler: Scheduler) extends Effect[Task] {

  def pure[A](value: A): Task[A] =
    Task.pure(value)

  def delay[A](value: => A): Task[A] =
    Task.delay(value)

  def fail[A](e: Throwable): Task[A] = Task.raiseError(e)

  def fork[A](m: => Task[A])(implicit ec: ExecutionContext): Task[A] =
    m.executeOn(Scheduler(ec))

  def unit: Task[Unit] =
    Task.unit

  def fromTry[A](value: => Try[A]): Task[A] =
    Task.fromTry(value)

  def start[A](m: => Task[A])(implicit ec: ExecutionContext): Task[Effect.Fiber[Task, A]] = m
    .executeOn(Scheduler(ec))
    .start
    .map(fiber => () => fiber.join)

  def promise[A](cb: (Either[Throwable, A] => Unit) => Unit): Task[A] =
    Task.async(cb)

  def promiseF[A](cb: (Either[Throwable, A] => Unit) => Task[Unit]): Task[A] =
    Task.asyncF(cb)

  def flatMap[A, B](m: Task[A])(f: A => Task[B]): Task[B] =
    m.flatMap(f)

  def map[A, B](m: Task[A])(f: A => B): Task[B] =
    m.map(f)

  def recover[A](m: Task[A])(f: PartialFunction[Throwable, A]): Task[A] =
    m.onErrorRecover(f)

  def sequence[A](in: List[Task[A]]): Task[List[A]] =
    Task.sequence(in)

  def runAsync[A](m: Task[A])(callback: Either[Throwable, A] => Unit): Unit = {
    m.runAsync(callback)
    ()
  }

  def run[A](m: Task[A]): Either[Throwable, A] =
    Try(m.runSyncUnsafe()).toEither

  def toFuture[A](m: Task[A]): Future[A] =
    m.runToFuture
}