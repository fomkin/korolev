/*
 * Copyright 2017-2018 Aleksey Fomkin
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

package korolev

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentSkipListSet

import scala.annotation.switch
import scala.collection.mutable
import scala.util.{Failure, Success}

final case class Stream[F[_]: Async, A](
   pull: () => F[Option[A]],
   finished: F[Unit],
   cancel: () => F[Unit],
   size: Option[Long]) { lhs =>

  /**
    * @see concat
    */
  def ++(rhs: Stream[F, A]): Stream[F, A] =
    concat(rhs)

  /**
    * Sequently concat two streams
    * {{{
    *   Stream(1,2,3) ++ Stream(4,5,6)
    *   // 1,2,3,4,5,6
    * }}}
    * @return
    */
  def concat(rhs: Stream[F, A]): Stream[F, A] =
    Stream(
      pull = () => Async[F].flatMap(lhs.pull()) { maybeValue =>
        if (maybeValue.nonEmpty) Async[F].pure(maybeValue)
        else rhs.pull()
      },
      finished = rhs.finished,
      cancel = () => {
        val lc = lhs.cancel()
        val rc = rhs.cancel()
        Async[F].flatMap(lc)(_ => rc)
      },
      size =
        for (ls <- lhs.size; rs <- rhs.size)
          yield ls + rs
    )

  def map[B](f: A => B): Stream[F, B] =
    Stream(
      pull = () => Async[F].map(lhs.pull()) { maybeValue =>
        maybeValue.map(f)
      },
      finished = lhs.finished,
      cancel = lhs.cancel,
      size = lhs.size
    )

  /**
    * Merges underlying streams concurrently.
    * @param concurrency number of concurrent underlying streams
    * {{{
    *   Stream(1, 2, 3) flatMapMerge(3) { x =>
    *     Stream(x + "a", x + "b", x + "c")
    *   }
    *   // 1a,2a,3a,1b,2b,3b,1c,2c,3c
    * }}}
    */
  def flatMapMerge[B](concurrency: Int)(f: A => Stream[F, B]): Stream[F, B] = {
    val streams: Array[Stream[F, B]] = new Array(concurrency)
    var takeFromCounter = 0
    def aux(): F[Option[B]] = {
      val takeFrom = takeFromCounter % concurrency
      val underlying = streams(takeFrom)
      takeFromCounter += 1
      if (underlying  == null) {
        Async[F].flatMap(lhs.pull()) {
          case Some(value) =>
            streams(takeFrom) = f(value)
            underlying.pull()
          case None =>
            streams(takeFrom) = null
            aux()
        }
      } else {
        underlying.pull()
      }
    }
    Stream[F, B](
      pull = () => aux(),
      finished = lhs.finished,
      cancel = lhs.cancel,
      size = None
    )
  }

  /**
    * @see flatMapConcat
    */
  def flatMap[B](f: A => Stream[F, B]): Stream[F, B] =
    flatMapMerge(1)(f)

  /**
    * Merges underlying streams to one line.
    * {{{
    *   Stream(1, 2, 3) flatMapMerge(2) { x =>
    *     Stream(x + "a", x + "b", x + "c")
    *   }
    *   // 1a,1b,1c,2a,2b,2c,3a,3b,3c
    * }}}
    */
  def flatMapConcat[B](f: A => Stream[F, B]): Stream[F, B] =
    flatMapMerge(1)(f)

  def fold[B](default: B)(f: (B, A) => F[B]): F[B] = {
    def aux(acc: B): F[B] = {
      Async[F].flatMap(pull()) {
        case Some(value) => Async[F].flatMap(f(acc, value))(acc => aux(acc))
        case None => Async[F].pure(acc)
      }
    }
    aux(default)
  }

//  def runScan[B](default: B)(f: (B, A) => F[B]): Stream[F, B] = {
//    def aux(acc: B): F[B] = {
//      Async[F].flatMap(pull()) {
//        case Some(value) => Async[F].flatMap(f(acc, value))(acc => aux(acc))
//        case None => Async[F].pure(acc)
//      }
//    }
//    aux(default)
//  }

  def foreach(f: A => F[Unit]): F[Unit] = {
    def aux(): F[Unit] = {
      Async[F].flatMap(pull()) {
        case Some(value) => Async[F].flatMap(f(value))(_ => aux())
        case None => Async[F].unit
      }
    }
    aux()
  }
}

object Stream {

  def eval[F[_]: Async, T](xs: T*): Stream[F, T] = {
    var n = 0
    var canceled = false
    val finished = Async[F].promise[Unit]
    Stream(
      pull = () => Async[F].delay {
        if (canceled || n == xs.length) {
          None
        } else {
          val res = xs(n)
          n += 1
          if (n == xs.length) {
            finished.complete(Success(()))
          }
          Some(res)
        }
      },
      finished = finished.async,
      cancel = () => Async[F].delay {
        canceled = true
        finished.complete(Success(()))
      },
      size = Some(xs.length)
    )
  }
}

/**
  * A function which returns new streams which
  * contains same elements as the parent stream.
  * This is helpful when you want to consume
  * content of the stream in few different places/
  * {{{
  *
  * }}}
  */
final class Hub[F[_]: Async, T](stream: Stream[F, T], bufferSize: Int) extends (() => F[Stream[F, T]]) {

  import scala.concurrent.duration._

  private val queues = new ConcurrentSkipListSet[Queue[F, T]]()

  private val puller = (x: T) => Async[F].delay {
    queues.forEach { q =>
      // 1 hour is means nothing because
      // offer is synchronous operation
      Async[F].run(q.offer(x), 1.hour)
    }
  }

  // Run this stream with puller.
  Async[F].runAsync(stream.foreach(puller)) {
    case Success(_) => queues.forEach(q => Async[F].run(q.close(), 1.hour))
    case Failure(e) => queues.forEach(q => Async[F].run(q.fail(e), 1.hour))
  }

  def apply(): F[Stream[F, T]] = Async[F].delay {
    val queue = Queue[F, T](bufferSize)
    queues.add(queue)
    Async[F].runAsync(queue.stream.finished) { _ =>
      // Remove queue if it was canceled/closed
      queues.remove(queue)
    }
    queue.stream
  }
}

object Hub {
  /**
    * @see Hub
    */
  def apply[F[_]: Async, T](stream: Stream[F, T], bufferSize: Int = Int.MaxValue): Hub[F, T] =
    new Hub(stream, bufferSize)
}

final case class Queue[F[_]: Async, T](offer: T => F[Unit],
                                       close: () => F[Unit],
                                       fail: Throwable => F[Unit],
                                       stream: Stream[F, T])

object Queue {

  private final class QueueBackend[F[_]: Async, T](maxSize: Int) {

    private var closed = false
    private var error: Throwable = _
    private var pending: Async.Promise[F, Option[T]] = _

    private val finished = Async[F].promise[Unit]
    private val underlyingQueue = mutable.Queue[T]()

    private val close = () => Async[F].delay {
      this.synchronized {
        if (pending != null) {
          pending.complete(Success(None))
          pending = null
        }
        finished.complete(Success(()))
        closed = true
      }
    }

    val queue: Queue[F, T] = Queue(
      offer = x => Async[F].delay {
        this.synchronized {
          if (underlyingQueue.size == maxSize) {
            // Remove head from queue if max size reached
            underlyingQueue.dequeue()
          }
          if (pending != null) {
            pending.complete(Success(Some(x)))
            pending = null
          } else {
            underlyingQueue.enqueue(x)
          }
        }
      },
      close = close,
      fail = e => Async[F].delay {
        this.synchronized {
          finished.complete(Failure(e))
          error = e
        }
      },
      stream = Stream[F, T](
        pull = () => {
          val xx = Async[F].delay {
            this.synchronized {
              if (!closed) {
                if (underlyingQueue.nonEmpty) {
                  Async[F].delay(Option(underlyingQueue.dequeue()))
                }
                else {
                  pending = Async[F].promise[Option[T]]
                  pending.async
                }
              }
              else Async[F].pure(Option.empty[T])
            }
          }
          Async[F].flatMap(xx)(identity)
        },
        finished = finished.async,
        cancel = close,
        size = None
      )
    )

  }

  def apply[F[_]: Async, T](maxSize: Int = Int.MaxValue): Queue[F, T] = {
    val backend = new QueueBackend[F, T](maxSize)
    backend.queue
  }
}

/**
  * @param pull Function which should be invoked recursively until it return None.
  * @param finished Completes when all bytes was pulled
  * @param cancel Cancels pulling. After that pull can return None or throws an exception (depends on implementation).
  * @param size known (or not) size
  */
final case class LazyBytes[F[_]](
    pull: () => F[Option[Array[Byte]]],
    finished: F[Unit],
    cancel: () => F[Unit],
    size: Option[Long])(implicit async: Async[F]) {

  /**
    * Folds all data to one byte array. Completes [[finished]].
    */
  def toStrict: F[Array[Byte]] = {
    def aux(acc: List[Array[Byte]]): F[List[Array[Byte]]] = {
      async.flatMap(pull()) {
        case Some(bytes) => aux(bytes :: acc)
        case None => async.delay(acc)
      }
    }
    async.map(aux(Nil)) { xs =>
      val length = xs.foldLeft(0)(_ + _.length)
      xs.foldRight(ByteBuffer.allocate(length))((a, b) => b.put(a)).array()
    }
  }

  /**
    * Same as [[toStrict]] but interprets bytes as UTF8 string.
    */
  def toStrictUtf8: F[String] = {
    async.map(toStrict)(bs => new String(bs, StandardCharsets.UTF_8))
  }

  /**
    * Drops all data. Completes [[finished]].
    */
  def discard(): F[Unit] = {
    def aux(): F[Unit] = async.flatMap(pull()) { x =>
      if (x.isEmpty) async.unit
      else aux()
    }
    aux()
  }
}

object LazyBytes {

  def apply[F[_]: Async](bytes: Array[Byte]): LazyBytes[F] = {
    var state = 0
    val finished = Async[F].promise[Unit]
    LazyBytes(
      pull = () => {
        (state: @switch) match {
          case 0 =>
            state = 1
            finished.complete(Success(()))
            Async[F].pure(Some(bytes))
          case 1 =>
            Async[F].pure(None)
        }
      },
      finished = finished.async,
      cancel = () => Async[F].unit,
      size = Some(bytes.length.toLong)
    )
  }

  def empty[F[_]](implicit async: Async[F]): LazyBytes[F] = {
    val it = async.pure(Option.empty[Array[Byte]])
    LazyBytes(() => it, async.unit, () => async.unit, Some(0L))
  }
}
