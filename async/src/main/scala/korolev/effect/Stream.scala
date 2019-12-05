package korolev.effect

import scala.util.Success

final case class Stream[F[_]: Effect, A](
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
      pull = () => Effect[F].flatMap(lhs.pull()) { maybeValue =>
        if (maybeValue.nonEmpty) Effect[F].pure(maybeValue)
        else rhs.pull()
      },
      finished = rhs.finished,
      cancel = () => {
        val lc = lhs.cancel()
        val rc = rhs.cancel()
        Effect[F].flatMap(lc)(_ => rc)
      },
      size =
        for (ls <- lhs.size; rs <- rhs.size)
          yield ls + rs
    )

  def map[B](f: A => B): Stream[F, B] =
    Stream(
      pull = () => Effect[F].map(lhs.pull()) { maybeValue =>
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
      Effect[F].flatMap(pull()) {
        case Some(value) => Effect[F].flatMap(f(acc, value))(acc => aux(acc))
        case None => Effect[F].pure(acc)
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
    val it = Effect[F].pure(Option.empty[T])
    Stream(() => it, Effect[F].unit, () => Effect[F].unit, Some(0L))
  }

  def eval[F[_]: Effect, T](xs: T*): Stream[F, T] = {
    var n = 0
    var canceled = false
    val finished = Effect[F].promise[Unit]
    Stream(
      pull = () => Effect[F].delay {
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
      finished = finished.effect,
      cancel = () => Effect[F].delay {
        canceled = true
        finished.complete(Success(()))
      },
      size = Some(xs.length)
    )
  }

  /**
    * Immediately gives same stream as `eventuallyStream`. Erases size.
    */
  def proxy[F[_]: Effect, T](eventuallyStream: F[Stream[F, T]]): Stream[F, T] = {
    Stream(
      pull = () => Effect[F].flatMap(eventuallyStream)(_.pull()),
      cancel = () => Effect[F].flatMap(eventuallyStream)(_.cancel()),
      finished = Effect[F].flatMap(eventuallyStream)(_.finished),
      size = None
    )
  }
}