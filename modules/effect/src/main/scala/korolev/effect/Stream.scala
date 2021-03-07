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

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

import korolev.effect.syntax._

abstract class Stream[F[_]: Effect, A] { self =>

  def pull(): F[Option[A]]
  
  def cancel() : F[Unit]

  /**
    * @see concat
    */
  def ++(rhs: Stream[F, A]): Stream[F, A] =
    concat(rhs)

  /**
    * Sequently concat two streams
    * {{{
    *   Stream.eval(1,2,3) ++ Stream.eval(4,5,6)
    *   // 1,2,3,4,5,6
    * }}}
    * @return
    */
  def concat(rhs: Stream[F, A]): Stream[F, A] = new Stream[F, A] { // TODO optimize me
    def pull(): F[Option[A]] =
      Effect[F].flatMap(self.pull()) { maybeValue =>
        if (maybeValue.nonEmpty) Effect[F].pure(maybeValue)
        else rhs.pull()
      }
    def cancel(): F[Unit] = {
      val lc = self.cancel()
      val rc = rhs.cancel()
      Effect[F].flatMap(lc)(_ => rc)
    }     
  }

  def collect[B](f: PartialFunction[A, B]): Stream[F, B] = new Stream[F, B] {
    val liftedF: A => Option[B] = f.lift
    def cancel(): F[Unit] = self.cancel()
    def pull(): F[Option[B]] = self.pull() flatMap {
      case None => Effect[F].pure(None)
      case Some(value) =>
        val result = liftedF(value)
        if (result.isEmpty) pull()
        else Effect[F].pure(result)
    }
  }

  def map[B](f: A => B): Stream[F, B] = new Stream[F, B] {
    def cancel(): F[Unit] = self.cancel()
    def pull(): F[Option[B]] = Effect[F]
      .map(self.pull()) { maybeValue => maybeValue.map(f) }
  }

  def mapAsync[B](f: A => F[B]): Stream[F, B] = new Stream[F, B] {
    def pull(): F[Option[B]] = self.pull() flatMap {
      case Some(value) => f(value).map(Some(_))
      case None => Effect[F].pure(None)
    }
    def cancel(): F[Unit] =
      self.cancel()
  }

  /**
    * Merges underlying streams concurrently.
    * @param concurrency number of concurrent underlying streams
    * {{{
    *   Stream.eval(1, 2, 3) flatMapMerge(3) { x =>
    *     Stream.eval(x + "a", x + "b", x + "c")
    *   }
    *   // 1a,2a,3a,1b,2b,3b,1c,2c,3c
    * }}}
    */
  def flatMapMerge[B](concurrency: Int)(f: A => Stream[F, B]): Stream[F, B] = new Stream[F, B]{
    val streams: Array[Stream[F, B]] = new Array(concurrency)
    var takeFromCounter = 0
    def aux(): F[Option[B]] = { // FIXME should be lazy
      val takeFrom = takeFromCounter % concurrency
      val underlying = streams(takeFrom)
      takeFromCounter += 1
      if (underlying  == null) {
        Effect[F].flatMap(self.pull()) {
          case Some(value) =>
            val newStream = f(value)
            streams(takeFrom) = newStream
            newStream.pull()
          case None =>
            streams(takeFrom) = null
            aux()
        }
      } else {
        underlying.pull()
      }
    }
    def pull(): F[Option[B]] = aux()
    def cancel(): F[Unit] = self.cancel()
  }

  def flatMapAsync[B](f: A => F[Stream[F, B]]): Stream[F, B] =
    flatMapMergeAsync(1)(f)

  def flatMapMergeAsync[B](concurrency: Int)(f: A => F[Stream[F, B]]): Stream[F, B] = new Stream[F, B] {
    val streams: Array[Stream[F, B]] = new Array(concurrency)
    var takeFromCounter = 0
    def aux(): F[Option[B]] = {
      val takeFrom = takeFromCounter % concurrency
      val underlying = streams(takeFrom)
      takeFromCounter += 1
      if (underlying == null) {
        self.pull() flatMap {
          case Some(value) =>
            extractNextStream(value, takeFrom)
          case None =>
            streams(takeFrom) = null
            aux()
        }
      } else {
        underlying.pull().flatMap {
          case Some(value) =>
            Effect[F].pure(Some(value))
          case None =>
            self.pull().flatMap {
              case Some(value) =>
                extractNextStream(value, takeFrom)
              case None =>
                Effect[F].pure(None)
            }
        }
      }
    }

    private def extractNextStream(value: A, takeFrom: Int): F[Option[B]] = {
      f(value).flatMap { newStream =>
        streams(takeFrom) = newStream
        newStream.pull()
      }
    }

    def pull(): F[Option[B]] = aux()
    def cancel(): F[Unit] = self.cancel()
  }

  /**
    * @see flatMapConcat
    */
  def flatMap[B](f: A => Stream[F, B]): Stream[F, B] =
    flatMapMerge(1)(f)

  /**
    * Merges underlying streams to one line.
    * {{{
    *   Stream.eval(1, 2, 3) flatMapConcat { x =>
    *     Stream.eval(x + "a", x + "b", x + "c")
    *   }
    *   // 1a,1b,1c,2a,2b,2c,3a,3b,3c
    * }}}
    */
  def flatMapConcat[B](f: A => Stream[F, B]): Stream[F, B] =
    flatMapMerge(1)(f)

  def foldAsync[B](default: B)(f: (B, A) => F[B]): F[B] = {
    def aux(acc: B): F[B] = {
      Effect[F].flatMap(pull()) {
        case Some(value) => Effect[F].flatMap(f(acc, value))(acc => aux(acc))
        case None => Effect[F].pure(acc)
      }
    }
    aux(default)
  }

  def fold[B](default: B)(f: (B, A) => B): F[B] = {
    def aux(acc: B): F[B] = {
      Effect[F].flatMap(pull()) {
        case Some(value) => aux(f(acc, value))
        case None => Effect[F].pure(acc)
      }
    }
    aux(default)
  }

  /**
    * React on values of the stream keeping it the same.
    * Useful when you want to track progress of downloading.
    *
    * {{{
    *   file
    *     .over(0L) {
    *       case (acc, chunk) =>
    *         val loaded = chunk.fold(acc)(_.length.toLong + acc)
    *         showProgress(loaded, file.bytesLength)
    *     }
    *     .to(s3bucket("my-large-file"))
    * }}}
    */
  def over[B](default: B)(f: (B, Option[A]) => F[B]): Stream[F, A] = new Stream[F, A] {
    var state: B = default
    def pull(): F[Option[A]] = self
      .pull()
      .flatMap { maybeValue =>
        f(state, maybeValue) map { newState =>
          state = newState
          maybeValue
        }
      }
    def cancel(): F[Unit] = self.cancel()
  }

  def to[U](f: Stream[F, A] => F[U]): F[U] =
    f(this)

  /**
    * Sort elements of the stream between "racks".
    *
    * {{{
    *   val List(girls, boys, queers) = persons.sort(3) {
    *     case person if person.isFemale => 0
    *     case person if person.isMale => 1
    *     case person => 2
    *   }
    * }}}

    * @param numRacks Number of racks.
    * @param f Takes element of the stream return number of rack.
    * @return List of streams appropriate to racks.
    */
  def sort(numRacks: Int)(f: A => Int): List[Stream[F, A]] = {
    val values = new Array[Option[A]](numRacks)
    val promises = new Array[Effect.Promise[Option[A]]](numRacks)
    val inProgress = new AtomicBoolean(false)
    (0 until numRacks).toList map { i =>
      new Stream[F, A] {
        def pull(): F[Option[A]] =
          Effect[F].promiseF { cb =>
            val maybeItem = values(i)
            if (maybeItem != null && maybeItem.isEmpty) {
              // End of parent stream
              Effect[F].delay(cb(Right(maybeItem)))
            } else if (maybeItem != null && maybeItem.nonEmpty) {
              // Contains value
              Effect[F].delay {
                cb(Right(maybeItem))
                values(i) = null
              }
            } else {
              promises(i) = cb
              // FIXME where is restart if cas failed?
              if (inProgress.compareAndSet(false, true)) {
                Effect[F].map(self.pull()) {
                  case maybeItem @ Some(item) =>
                    val j = f(item)
                    val cb = promises(j)
                    if (cb != null) {
                      promises(j) = null
                      inProgress.compareAndSet(true, false)
                      cb(Right(maybeItem))
                    } else {
                      values(j) = maybeItem
                      inProgress.compareAndSet(true, false)
                    }
                  case None =>
                    for (j <- 0 until numRacks)
                      values(j) = None
                    inProgress.compareAndSet(true, false)
                    promises.foreach(_(Right(None)))
                }
              } else {
                Effect[F].unit
              }
            }
          }
        def cancel(): F[Unit] = self.cancel()
      }
    }
  }

  def handleConsumed: (F[Unit], Stream[F, A]) = {
    var consumed = false
    var callback: Effect.Promise[Unit] = null
    val handler = Effect[F].promise[Unit] { cb =>
      if (consumed) cb(Right(()))
      else callback = cb
    }
    val downstream = new Stream[F, A] {
      def pull(): F[Option[A]] = self.pull().map {
        case None =>
          consumed = true
          if (callback != null) {
            val cb = callback
            callback = null
            cb(Right(()))
          }
          None
        case maybeItem => maybeItem
      }
      def cancel(): F[Unit] = self.cancel()
    }
    (handler, downstream)
  }

  def foreach(f: A => F[Unit]): F[Unit] = {
    def aux(): F[Unit] = {
      Effect[F].flatMap(pull()) {
        case Some(value) => Effect[F].flatMap(f(value))(_ => aux())
        case None => Effect[F].unit
      }
    }
    aux()
  }
}

object Stream {

  def empty[F[_]: Effect, T]: Stream[F, T] = {
    new Stream[F, T] {
      val pull: F[Option[T]] = Effect[F].pure(Option.empty[T])
      val cancel: F[Unit] = Effect[F].unit
    }
  }

//  def never[F[_]: Effect, T]: Stream[F, T] =  new Stream[F, T] {
//    val pull: F[Option[T]] = Effect[F].never
//    val cancel: F[Unit] = Effect[F].unit
//  }

  def apply[T](xs: T*): Stream.Template[T] = new Template[T] {
    def mat[F[_]: Effect](): F[Stream[F, T]] = Effect[F].delay {
      new Stream[F, T] {
        var n = 0
        var canceled = false
        def pull(): F[Option[T]] = Effect[F].delay {
          if (canceled || n == xs.length) {
            None
          } else {
            val res = xs(n)
            n += 1
            Some(res)
          }
        }
        def cancel(): F[Unit] = Effect[F].delay {
          canceled = true
        }
      }
    }
  }

  /**
    * Immediately gives same stream as `eventuallyStream`.
    */
  def proxy[F[_]: Effect, T](eventuallyStream: F[Stream[F, T]]): Stream[F, T] = {
    new Stream[F, T] {
      def pull(): F[Option[T]] = Effect[F].flatMap(eventuallyStream)(_.pull())
      def cancel(): F[Unit] = Effect[F].flatMap(eventuallyStream)(_.cancel())
    }
  }


  def unfold[F[_]: Effect, S, T](default: S, doCancel: () => F[Unit] = null)
                                (loop: S => F[(S, Option[T])]): Stream[F, T] = {
    new Stream[F, T] {
      @volatile private var state = default
      def pull(): F[Option[T]] = loop(state)
        .map {
          case (newState, None) =>
            state = newState
            None
          case (newState, maybeElem) =>
            state = newState
            maybeElem
        }
      def cancel(): F[Unit] =
        if (doCancel != null) doCancel()
        else Effect[F].unit
    }
  }

  def unfoldResource[F[_]: Effect, R <: Closeable, S, T](create: => F[R],
                                                         default: S,
                                                         loop: (R, S) => F[(S, Option[T])]): F[Stream[F, T]] =

    create.map { resource =>
      new Stream[F, T] {
        @volatile private var state = default
        def pull(): F[Option[T]] = loop(resource, state)
          .map {
            case (newState, None) =>
              state = newState
              resource.close()
              None
            case (newState, maybeElem) =>
              state = newState
              maybeElem
          }
          .recover {
            case error =>
              resource.close()
              throw error
          }

        def cancel(): F[Unit] = Effect[F].delay {
          resource.close()
        }
      }
    }

  trait Template[A] {
    def mat[F[_]: Effect](): F[Stream[F, A]]
  }
}
