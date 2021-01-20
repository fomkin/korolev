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
import korolev.effect.syntax._

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.collection.immutable.{Queue => IQueue}

/**
 * Nonblocking, concurrent, asynchronous queue.
 */
class Queue[F[_]: Effect, T](maxSize: Int) {

  import Queue._

  private val state = new AtomicReference(Queue.State[T]())

  private final class QueueStream extends Stream[F, T] {

    def pull(): F[Option[T]] = Effect[F].promise { cb =>
      @tailrec
      def aux(): Unit = {
        val ref = state.get()
        ref.error match {
          case Some(error) => cb(Left(error))
          case None if ref.closed => cb(noneToken)
          case None if ref.queue.nonEmpty =>
            val (value, updatedQueue) = ref.queue.dequeue
            val canOfferCallback = ref.canOfferCallbacks.headOption
            val newValue = ref.copy(
              queue = updatedQueue,
              canOfferCallbacks = ref.canOfferCallbacks.drop(1)
            )
            if (state.compareAndSet(ref, newValue)) {
              cb(Right(Option(value)))
              canOfferCallback.foreach(_(unitToken))
            } else {
              aux()
            }
          case None if ref.stopped => cb(noneToken)
          case None =>
            val updatedPc = cb :: ref.pullCallbacks
            val newValue = ref.copy(pullCallbacks = updatedPc)
            if (!state.compareAndSet(ref, newValue)) {
              aux()
            }
        }
      }
      aux()
    }

    def cancel(): F[Unit] = Effect[F].delay {
      unsafeClose()
      @tailrec def aux(): Unit = {
        val ref = state.get
        val newValue = ref.copy(cancelCallbacks = Nil)
        if (state.compareAndSet(ref, newValue)) {
          ref.cancelCallbacks.foreach(cb => cb(unitToken))
        } else {
          aux()
        }
      }
      aux()
    }
  }

  /**
   * Strict version of [[offer]]. Still thread safe.
   */
  def offerUnsafe(item: T): Boolean = {
    @tailrec
    def aux(): Boolean = {
      val ref = state.get()
      if (!ref.stopped && !ref.closed) {
        // Pull callbacks can be nonempty
        // if queue was empty when pull was ran.
        if (ref.pullCallbacks.nonEmpty) {
          val newValue = ref.copy(pullCallbacks = Nil)
          if (state.compareAndSet(ref, newValue)) {
            val token = Right(Some(item))
            ref.pullCallbacks.foreach(cb => cb(token))
            true
          } else {
            aux()
          }
        } else if (ref.queue.size < maxSize) {
          val updatedQueue = ref.queue.enqueue(item)
          val newValue = ref.copy(queue = updatedQueue)
          if (state.compareAndSet(ref, newValue)) {
            true
          } else {
            aux()
          }
        } else {
          false
        }
      } else {
        false
      }
    }
    aux()
  }

  def unsafeStop(): Unit = {
    @tailrec def aux(): Unit = {
      val ref = state.get
      if (state.compareAndSet(ref, ref.copy(stopped = true))) {
        if (ref.queue.isEmpty) {
          ref.canOfferCallbacks.foreach(cb => cb(unitToken))
          ref.pullCallbacks.foreach(cb => cb(noneToken))
        }
      } else  {
        aux()
      }
    }
    aux()
  }

  def unsafeClose(): Unit = {
    @tailrec
    def aux(): Unit = {
      val ref = state.get
      if (!ref.closed) {
        val newValue = ref.copy(canOfferCallbacks = Nil, pullCallbacks = Nil, closed = true)
        if (state.compareAndSet(ref, newValue)) {
          ref.canOfferCallbacks.foreach(cb => cb(unitToken))
          ref.pullCallbacks.foreach(cb => cb(noneToken))
        } else {
          aux()
        }
      }
    }
    aux()
  }

  /**
   * Signals that queue size became less than [[maxSize]].
   * @example {{{
   * def aux(): F[Unit] = queue.offer(o).flatMap {
   *   case false => queue.canOffer *> aux()
   *   case true => Effect[F].unit
   * }
   * aux()
   * }}}
   */
  def canOffer: F[Unit] = Effect[F].promise { cb =>
    @tailrec
    def aux(): Unit = {
      val ref = state.get
      if (ref.closed || ref.stopped || ref.queue.size < maxSize) {
        cb(unitToken)
      } else {
        val newValue = ref.copy(canOfferCallbacks = cb :: ref.canOfferCallbacks)
        if (!state.compareAndSet(ref, newValue)) {
          aux()
        }
      }
    }
    aux()
  }

  def failUnsafe(e: Throwable): Unit = {
    @tailrec
    def aux(): Unit = {
      val ref = state.get
      val newValue = ref.copy(canOfferCallbacks = Nil, pullCallbacks = Nil, error = Some(e))
      if (state.compareAndSet(ref, newValue)) {
        ref.canOfferCallbacks.foreach(cb => cb(unitToken))
        ref.pullCallbacks.foreach(cb => cb(Left(e)))
      } else {
        aux()
      }
    }
    aux()
  }

  /**
   * Offers `item` to the queue.
   * @return true is ok and false if [[maxSize]] reached or queue is stopped.
   */
  def offer(item: T): F[Boolean] =
    Effect[F].delay(offerUnsafe(item))

  /**
   * Enqueue item. If [[maxSize]] reached waits until queue will decrease.
   */
  def enqueue(item: T): F[Unit] = {
    def aux(): F[Unit] = {
      val ref = state.get
      offer(item).flatMap {
        case true => Effect[F].unit
        case false if ref.stopped || ref.closed => Effect[F].unit
        case false => canOffer *> aux()
      }
    }
    aux()
  }

  /**
   * Disallow to offer new items.
   * Stream ends with last item.
   */
  def stop(): F[Unit] =
    Effect[F].delay(unsafeStop())

  /**
   * Immediately stop offering and pulling items from the queue.
   * @return
   */
  def close(): F[Unit] =
    Effect[F].delay(unsafeClose())

  def fail(e: Throwable): F[Unit] =
    Effect[F].delay(failUnsafe(e))

  /**
   * Resolves only if `stream.cancel` ran.
   * @return
   */
  def cancelSignal: F[Unit] = Effect[F].promise { cb =>
    @tailrec def aux(): Unit = {
      val ref = state.get
      val newValue = ref.copy(cancelCallbacks = cb :: ref.cancelCallbacks)
      if (!state.compareAndSet(ref, newValue)) {
        aux()
      }
    }
    aux()
  }

  val stream: Stream[F, T] = new QueueStream()
}

object Queue {

  private case class State[T](stopped: Boolean = false,
                              closed: Boolean = false,
                              error: Option[Throwable] = None,
                              pullCallbacks: List[Promise[Option[T]]] = Nil,
                              canOfferCallbacks: List[Promise[Unit]] = Nil,
                              cancelCallbacks: List[Promise[Unit]] = Nil,
                              queue: IQueue[T] = IQueue.empty)

  private final val unitToken = Right(())
  private final val noneToken = Right(None)

  def apply[F[_]: Effect, T](maxSize: Int = Int.MaxValue): Queue[F, T] =
    new Queue[F, T](maxSize)
}