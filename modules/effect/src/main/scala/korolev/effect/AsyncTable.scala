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

import korolev.effect.AsyncTable.{AlreadyContainsKeyException, RemovedBeforePutException}

import korolev.effect.Effect.Promise

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import korolev.effect.syntax._

final class AsyncTable[F[_] : Effect, K, V](elems: Seq[(K, V)]) {

  private type Callbacks = List[Promise[V]]
  private type Result = Either[Throwable, V]

  private val state = new AtomicReference[Map[K, Either[Callbacks, Result]]](
    elems.toVector.map { case (k, v) => (k, Right(Right(v))) }.toMap
  )


  def get(key: K): F[V] = Effect[F].promise[V] { cb =>
    @tailrec
    def aux(): Unit = {
      val ref = state.get
      ref.get(key) match {
        case Some(Right(value)) => cb(value)
        case Some(Left(xs)) =>
          val newValue = ref.updated(key, Left(cb :: xs))
          if (!state.compareAndSet(ref, newValue)) {
            aux()
          }
        case None =>
          val newValue = ref.updated(key, Left(cb :: Nil))
          if (!state.compareAndSet(ref, newValue)) {
            aux()
          }
      }
    }
    aux()
  }

  def getFill(key: K)(f: => F[V]): F[V] = Effect[F].promiseF[V] { cb =>
    @tailrec
    def aux(): F[Unit] = {
      val ref = state.get
      ref.get(key) match {
        case Some(Right(value)) =>
          Effect[F].delay(cb(value))
        case Some(Left(xs)) =>
          val newValue = ref.updated(key, Left(cb :: xs))
          if (state.compareAndSet(ref, newValue)) {
            Effect[F].unit
          } else {
            aux()
          }
        case None =>
          val newValue = ref.updated(key, Left(cb :: Nil))
          if (state.compareAndSet(ref, newValue)) {
            f.flatMap(v => put(key, v))
          } else {
            aux()
          }
      }
    }
    aux()
  }

  def getImmediately(key: K): F[Option[V]] =
    Effect[F].delay {
      val table = state.get
      for {
        lr <- table.get(key)
        res <- lr.toOption
        v <- res.toOption
      } yield v
    }

  def put(key: K, value: V): F[Unit] =
    putEither(key, Right(value))

  def fail(key: K, error: Throwable): F[Unit] =
    putEither(key, Left(error))

  def putEither(key: K, errorOrValue: Either[Throwable, V], silent: Boolean = false): F[Unit] =
    Effect[F].delay {
      @tailrec
      def aux(): Unit = {
        val ref = state.get
        ref.get(key) match {
          case Some(Right(_)) if silent => () // Do nothing
          case Some(Right(_)) =>
            throw AlreadyContainsKeyException(key)
          case Some(Left(callbacks)) =>
            val newValue = ref.updated(key, Right(errorOrValue))
            if (state.compareAndSet(ref, newValue)) {
              callbacks.foreach(_ (errorOrValue))
            } else {
              aux()
            }
          case None =>
            val newValue = ref.updated(key, Right(errorOrValue))
            if (!state.compareAndSet(ref, newValue)) {
              aux()
            }
        }
      }
      aux()
    }

  def remove(key: K): F[Unit] = Effect[F].delay {
    @tailrec
    def aux(): Unit = {
      val ref = state.get
      val value = ref.get(key)
      val updatedRef = ref - key
      if (state.compareAndSet(ref, updatedRef)) {
        value match {
          case Some(Left(callbacks)) =>
            val result = Left(RemovedBeforePutException(key))
            callbacks.foreach(_(result))
          case _ => ()
        }
      } else {
        aux()
      }
    }
    aux()
  }
}

object AsyncTable {

  final case class RemovedBeforePutException(key: Any)
    extends Exception(s"Key '$key' removed before value was added.")

  final case class AlreadyContainsKeyException(key: Any)
    extends Exception(s"Already contains value for '$key'.")

  def apply[F[_] : Effect, K, V](elems: (K, V)*) =
    new AsyncTable[F, K, V](elems)

  def unsafeCreateEmpty[F[_] : Effect, K, V] =
    new AsyncTable[F, K, V](Nil)

  def empty[F[_] : Effect, K, V]: F[AsyncTable[F, K, V]] =
    Effect[F].delay(new AsyncTable[F, K, V](Nil))
}
