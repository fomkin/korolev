package korolev.effect

import syntax._

abstract class Stream[F[_]: Effect, A] { lhs =>

  def pull(): F[Option[A]]
  
  def cancel() : F[Unit]

  def consumed: F[Unit]

  def size: Option[Long]
  
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
  def concat(rhs: Stream[F, A]): Stream[F, A] = new Stream[F, A] {
    val consumed: F[Unit] = rhs.consumed
    val size: Option[Long] =
      for (ls <- lhs.size; rs <- rhs.size)
        yield ls + rs
    
    def pull(): F[Option[A]] = 
      Effect[F].flatMap(lhs.pull()) { maybeValue =>
        if (maybeValue.nonEmpty) Effect[F].pure(maybeValue)
        else rhs.pull()
      }
    def cancel(): F[Unit] = {
      val lc = lhs.cancel()
      val rc = rhs.cancel()
      Effect[F].flatMap(lc)(_ => rc)
    }     
  }

  def map[B](f: A => B): Stream[F, B] = new Stream[F, B] {
    val consumed: F[Unit] = lhs.consumed
    val size: Option[Long] = lhs.size
    def cancel(): F[Unit] = lhs.cancel()
    def pull(): F[Option[B]] = Effect[F]
      .map(lhs.pull()) { maybeValue => maybeValue.map(f) }
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
    def aux(): F[Option[B]] = {
      val takeFrom = takeFromCounter % concurrency
      val underlying = streams(takeFrom)
      takeFromCounter += 1
      if (underlying  == null) {
        Effect[F].flatMap(lhs.pull()) {
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
    def pull(): F[Option[B]] = aux()
    def cancel(): F[Unit] = lhs.cancel()
    val consumed: F[Unit] = lhs.consumed
    val size: Option[Long] = None
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

  def fold[B](default: B)(f: (B, A) => F[B]): F[B] = {
    def aux(acc: B): F[B] = {
      Effect[F].flatMap(pull()) {
        case Some(value) => Effect[F].flatMap(f(acc, value))(acc => aux(acc))
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
    *   file // :LazyBytes
    *     .chunks
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
    def pull(): F[Option[A]] = lhs
      .pull()
      .flatMap { maybeValue =>
        f(state, maybeValue) map { newState =>
          state = newState
          maybeValue
        }
      }
    def cancel(): F[Unit] = lhs.cancel()
    val consumed: F[Unit] = lhs.consumed
    val size: Option[Long] = lhs.size
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
      val consumed: F[Unit] = Effect[F].unit
      val size: Option[Long] = Some(0L)
    }
  }

  def eval[F[_]: Effect, T](xs: T*): Stream[F, T] =
    new Stream[F, T] {
      var n = 0
      var canceled = false
      var consumedCallback: Either[Throwable, Unit] => Unit = null
      def pull(): F[Option[T]] = Effect[F].delay {
        if (canceled || n == xs.length) {
          None
        } else {
          val res = xs(n)
          n += 1
          if (n == xs.length) {
            if (consumedCallback != null)
              consumedCallback(Right(()))
          }
          Some(res)
        }
      }
      def cancel(): F[Unit] = Effect[F].delay {
        canceled = true
        if (consumedCallback != null)
          consumedCallback(Right(()))
      }
      val size: Option[Long] = Some(xs.length)
      val consumed: F[Unit] = Effect[F].promise[Unit] { cb =>
        if (canceled || n == xs.length) cb(Right(()))
        else consumedCallback = cb
      }
    }

  /**
    * Immediately gives same stream as `eventuallyStream`. Erases size.
    */
  def proxy[F[_]: Effect, T](eventuallyStream: F[Stream[F, T]]): Stream[F, T] = {
    new Stream[F, T] {
      def pull(): F[Option[T]] = Effect[F].flatMap(eventuallyStream)(_.pull())
      def cancel(): F[Unit] = Effect[F].flatMap(eventuallyStream)(_.cancel())
      val consumed: F[Unit] = Effect[F].flatMap(eventuallyStream)(_.consumed)
      val size: Option[Long] = None
    }
  }
}