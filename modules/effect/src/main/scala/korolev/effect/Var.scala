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

import korolev.effect.syntax.*

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

trait Var[F[_], A] {
  def get: F[A]
  def set(value: A): F[Unit]
  // Update
  def tryUpdate(f: A => A): F[Option[A]]
  def tryUpdateF(f: A => F[A]): F[Option[A]]
  def update(f: A => A): F[A]
  def updateF(f: A => F[A]): F[A]
  // Modify
  def tryModify[B](f: A => (A, B)): F[Option[B]]
//  def tryModifyF[B](f: A => F[(A, B)]): F[Option[B]]
  def modify[B](f: A => (A, B)): F[B]
  def modifyF[B](f: A => F[(A, B)]): F[B]
}

class VarImpl[F[_]: Effect, A](atomic: AtomicReference[A]) extends Var[F, A] {

  def get: F[A] = Effect[F].delay(atomic.get())

  def set(value: A): F[Unit] = Effect[F].delay(atomic.set(value))

  // Update

  def tryUpdate(f: A => A): F[Option[A]] = Effect[F].delay {
    val x = atomic.get()
    val x2 = f(x)
    if (atomic.compareAndSet(x, x2)) Some(x2)
    else None
  }

  def tryUpdateF(f: A => F[A]): F[Option[A]] = Effect[F].delayAsync {
    val x = atomic.get()
    f(x).map { x2 =>
      if (atomic.compareAndSet(x, x2)) Some(x2)
      else None
    }
  }

  def update(f: A => A): F[A] = Effect[F].delay {
    @tailrec def aux(): A = {
      val x = atomic.get()
      val x2 = f(x)
      if (atomic.compareAndSet(x, x2)) x2
      else aux()
    }
    aux()
  }

  def updateF(f: A => F[A]): F[A] = Effect[F].delayAsync {
    def aux(): F[A] = {
      val x = atomic.get()
      f(x).flatMap { x2 =>
        if (atomic.compareAndSet(x, x2)) Effect[F].pure(x2)
        else aux()
      }
    }
    aux()
  }

  // Modify

  def tryModify[B](f: A => (A, B)): F[Option[B]] = Effect[F].delay {
    val x = atomic.get()
    val (x2, ret) = f(x)
    if (atomic.compareAndSet(x, x2)) Some(ret)
    else None
  }

  def tryModifyF[B](f: A => (A, B)): F[Option[B]] = Effect[F].delay {
    val x = atomic.get()
    val (x2, ret) = f(x)
    if (atomic.compareAndSet(x, x2)) Some(ret)
    else None
  }

  def modify[B](f: A => (A, B)): F[B] = Effect[F].delay {
    @tailrec def aux(): B = {
      val x = atomic.get()
      val (x2, ret) = f(x)
      if (atomic.compareAndSet(x, x2)) ret
      else aux()
    }
    aux()
  }

  def modifyF[B](f: A => F[(A, B)]): F[B] = Effect[F].delayAsync {
    def aux(): F[B] = {
      val x = atomic.get()
      f(x).flatMap { case (x2, ret) =>
        if (atomic.compareAndSet(x, x2)) Effect[F].pure(ret)
        else aux()
      }
    }
    aux()
  }

}

object Var {

  def apply[F[_]]: ApplyMake[F] =
    new ApplyMake[F](true)

  class ApplyMake[F[_]](dummy: Boolean) extends AnyVal {
    def make[T](initialValue: T)(implicit effect: Effect[F]): F[Var[F, T]] =
      Effect[F].delay(new VarImpl(new AtomicReference[T](initialValue)))

    def makeUnsafe[T](initialValue: T)(implicit effect: Effect[F]): Var[F, T] =
      new VarImpl(new AtomicReference[T](initialValue))
  }

}