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

import scala.collection.mutable

class Queue[F[_]: Effect, T](maxSize: Int) {

  protected var stopped = false

  def offer(item: T): F[Unit] =
    Effect[F].delay(offerUnsafe(item))

  def offerUnsafe(item: T): Unit = if (!stopped) {
    underlyingQueue.synchronized {
      if (underlyingQueue.size == maxSize) {
        // Remove head from queue if max size reached
        underlyingQueue.dequeue()
        ()
      }
      if (pending != null) {
        val cb = pending
        pending = null
        cb(Right(Some(item)))
      } else {
        underlyingQueue.enqueue(item)
        ()
      }
    }
  }

  /**
    * Disallow to offer new items.
    * Stream ends with last item.
    */
  def stop(): F[Unit] =
    Effect[F].delay(unsafeStop())

  def close(): F[Unit] =
    Effect[F].delay(unsafeClose())

  def unsafeStop(): Unit =
    stopped = true

  def unsafeClose(): Unit =
    underlyingQueue.synchronized {
      if (pending != null) {
        val cb = pending
        pending = null
        cb(Right(None))
      }
      closed = true
    }

  def fail(e: Throwable): F[Unit] =
    Effect[F].delay {
      underlyingQueue.synchronized {
        error = e
        if (pending != null) {
          val cb = pending
          pending = null
          cb(Left(e))
        }
      }
    }

  private final class QueueStream extends Stream[F, T] {

    def pull(): F[Option[T]] = Effect[F].promise { cb =>
      underlyingQueue.synchronized {
        if (error != null) cb(Left(error))
        else if (closed) cb(Right(None)) else {
          if (underlyingQueue.nonEmpty) {
            val elem = underlyingQueue.dequeue()
            cb(Right(Some(elem)))
          } else if (stopped) {
            cb(Right(None))
          } else {
            pending = cb
          }
        }
      }
    }

    def cancel(): F[Unit] = close()
  }

  val stream: Stream[F, T] = new QueueStream()

  @volatile private var closed = false
  @volatile private var error: Throwable = _
  @volatile private var pending: Effect.Promise[Option[T]] = _
  private val underlyingQueue: mutable.Queue[T] = mutable.Queue.empty[T]
}

object Queue {

  def apply[F[_]: Effect, T](maxSize: Int = Int.MaxValue): Queue[F, T] =
    new Queue[F, T](maxSize)
}