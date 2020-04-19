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

import java.util.concurrent.atomic.AtomicBoolean

import korolev.effect.syntax._

import scala.collection.concurrent.TrieMap

/**
  * A function which returns new streams which
  * contains same elements as the parent stream.
  * This is helpful when you want to consume
  * content of the stream in few different places.
  */
final class Hub[F[_]: Effect, T](upstream: Stream[F, T], bufferSize: Int) {

  @volatile private var closed = false

  private val queues = TrieMap.empty[Queue[F, T], Unit]
  private val inProgress = new AtomicBoolean(false)

  private final class StreamOnePullAtTime(thisQueue: Queue[F, T]) extends Stream[F, T] {

    private def begin(): F[Boolean] =
      Effect[F].delay(inProgress.compareAndSet(false, true))

    private def end(): F[Unit] =
      Effect[F].delay {
        val end = inProgress.compareAndSet(true, false)
        if (!end) throw new IllegalStateException("Can't be false")
      }

    private def pullUpstream(): F[Option[T]] = upstream
      .pull()
      .map { maybeItem =>
        maybeItem match {
          case Some(item) =>
            queues.keysIterator.foreach { queue =>
              if (queue != thisQueue)
                queue.offerUnsafe(item)
            }
          case None =>
            closed = true
            queues.keysIterator.foreach(_.closeUnsafe())
        }
        maybeItem
      }

    def pull(): F[Option[T]] = begin()
      .flatMap { began =>
        if (!began) thisQueue.stream.pull()
        else for (result <- pullUpstream(); _ <- end())
          yield result
      }

    def cancel(): F[Unit] = thisQueue.close()
  }

  private final class QueueRemoveFromHubOnClose extends Queue[F, T](bufferSize) {
    override def closeUnsafe(): Unit = {
      queues.remove(this)
      super.closeUnsafe()
    }
  }

  def newStream(): F[Stream[F, T]] = Effect[F].delay {
    if (closed)
      throw new IllegalStateException("Hub is closed")
    val queue = new QueueRemoveFromHubOnClose()
    val stream = new StreamOnePullAtTime(queue)
    queues.put(queue, ())
    stream
  }
}

object Hub {

  /**
    * @see Hub
    */
  def apply[F[_]: Effect, T](stream: Stream[F, T], bufferSize: Int = Int.MaxValue): Hub[F, T] =
    new Hub(stream, bufferSize)
}