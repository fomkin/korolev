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

import korolev.effect.Effect.Promise

import scala.collection.mutable

class Queue[F[_]: Effect, T](maxSize: Int) {

  import Queue._

  def offer(item: T): F[Unit] =
    Effect[F].delay(offerUnsafe(item))

  def join: F[Unit] = Effect[F].promise { cb =>
    this.synchronized {
      if (closed || underlyingQueue.size < maxSize) {
        cb(unitToken)
      } else {
        joinCallbacks = cb :: joinCallbacks
      }
    }
  }

  def offerUnsafe(item: T): Unit = underlyingQueue.synchronized {
    if (!stopped) {
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
      val joinXs = joinCallbacks
      joinCallbacks = List.empty
      joinXs.foreach(_ (unitToken))
      if (pending != null) {
        val cb = pending
        pending = null
        cb(noneToken)
      }
      closed = true
    }

  def fail(e: Throwable): F[Unit] =
    Effect[F].delay {
      underlyingQueue.synchronized {
        error = e
        val xs = joinCallbacks
        joinCallbacks = Nil
        if (pending != null) {
          val cb = pending
          pending = null
          cb(Left(e))
        }
        xs.foreach(_ (unitToken))
      }
    }

  def cancelSignal: F[Unit] = Effect[F].promise { cb =>
    this.synchronized {
      cancelCallbacks = cb :: cancelCallbacks
    }
  }

  private final class QueueStream extends Stream[F, T] {

    def pull(): F[Option[T]] = Effect[F].promise { cb =>
      underlyingQueue.synchronized {
        if (error != null) cb(Left(error))
        else if (closed) cb(noneToken) else {
          if (underlyingQueue.nonEmpty) {
            val xs = joinCallbacks
            joinCallbacks = Nil
            cb(Right(Option(underlyingQueue.dequeue())))
            xs.foreach(_ (unitToken))
          } else if (stopped) {
            cb(noneToken)
          } else {
            pending = cb
          }
        }
      }
    }

    def cancel(): F[Unit] = Effect[F].delay {
      close()
      val xs = cancelCallbacks
      cancelCallbacks = Nil
      xs.foreach(_ (unitToken))
      unsafeClose()
    }
  }

  val stream: Stream[F, T] = new QueueStream()

  @volatile protected var stopped = false
  @volatile private var closed = false
  @volatile private var error: Throwable = _
  @volatile private var pending: Promise[Option[T]] = _
  @volatile private var joinCallbacks = List.empty[Promise[Unit]]
  @volatile private var cancelCallbacks = List.empty[Promise[Unit]]

  private val underlyingQueue: mutable.Queue[T] = mutable.Queue.empty[T]
}

object Queue {

  private final val unitToken = Right(())
  private final val noneToken = Right(None)

  def apply[F[_]: Effect, T](maxSize: Int = Int.MaxValue): Queue[F, T] =
    new Queue[F, T](maxSize)
}