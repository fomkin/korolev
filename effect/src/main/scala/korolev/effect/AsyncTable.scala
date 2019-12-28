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

import scala.collection.mutable
import korolev.effect.Effect.Promise

final class AsyncTable[F[_]: Effect, K, V](elems: Seq[(K, V)]) {

  private type Callbacks = List[Promise[V]]
  private type Result = Either[Throwable, V]
  private val table = mutable.Map[K, Either[Callbacks, Result]](elems.map { case (k, v) => (k, Right(Right(v))) }: _*)

  def get(key: K): F[V] =
    Effect[F].promise[V] { cb =>
      table.synchronized {
        table.get(key) match {
          case Some(Right(value)) => cb(value)
          case Some(Left(xs))     => table.update(key, Left(cb :: xs))
          case None               => table.update(key, Left(cb :: Nil))
        }
      }
    }

  def put(key: K, value: V): F[Unit] =
    putEither(key, Right(value))

  def fail(key: K, error: Throwable): F[Unit] =
    putEither(key, Left(error))

  def putEither(key: K, errorOrValue: Either[Throwable, V]): F[Unit] =
    Effect[F].delay {
      table.synchronized {
        table.remove(key) match {
          case Some(Right(_)) =>
            throw AlreadyContainsKeyException(key)
          case Some(Left(callbacks)) =>
            table.update(key, Right(errorOrValue))
            callbacks.foreach(_(errorOrValue))
          case None =>
            table.update(key, Right(errorOrValue))
        }
      }
    }

  def remove(key: K): F[Unit] =
    Effect[F].delay {
      table.synchronized {
        table.remove(key) match {
          case Some(Left(callbacks)) =>
            val result = Left(RemovedBeforePutException(key))
            callbacks.foreach(_(result))
          case _ => ()
        }
      }
    }
}

object AsyncTable {

  final case class RemovedBeforePutException(key: Any)
      extends Exception(s"Key $key removed before value was put to table.")

  final case class AlreadyContainsKeyException(key: Any)
      extends Exception(s"This table already contains value for $key")

  def apply[F[_]: Effect, K, V](elems: (K, V)*) =
    new AsyncTable[F, K, V](elems)

  def empty[F[_]: Effect, K, V] =
    new AsyncTable[F, K, V](Nil)
}
