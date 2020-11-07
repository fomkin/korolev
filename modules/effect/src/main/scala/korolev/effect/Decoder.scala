package korolev.effect

import korolev.effect.Decoder.Action
import korolev.effect.syntax._

class Decoder[F[_]: Effect, A](upstream: Stream[F, A]) extends Stream[F, A] { self =>

  @volatile private var takenBackQueue: List[A] = Nil

  def pull(): F[Option[A]] = Effect[F].delayAsync {
    takenBackQueue match {
      case Nil => upstream.pull()
      case x :: xs =>
        takenBackQueue = xs
        Effect[F].pure(Option(x))
    }
  }

  def cancel(): F[Unit] =
    upstream.cancel()

  private def unsafeTakeBack(item: A): Unit =
    takenBackQueue = item :: takenBackQueue

  def decode[S, B](default: => S)(f: (S, A) => (S, Action[A, B])): Decoder[F, B] =
    decodeAsync(default)((s, a) => Effect[F].pure(f(s, a)))

  def decodeAsync[S, B](default: => S)(f: (S, A) => F[(S, Action[A, B])]): Decoder[F, B] = Decoder(
    new Stream[F, B] {
      var state: S = default
      var finished = false
      def pull(): F[Option[B]] = Effect[F].delayAsync {
        if (finished) Effect[F].pure(None)
        else {
          self.pull().flatMap {
            case Some(x) =>
              f(state, x) flatMap {
                case (newState, Action.PushFinish(value)) =>
                  state = newState
                  finished = true
                  Effect[F].pure(Some(value))
                case (newState, Action.Push(value)) =>
                  state = newState
                  Effect[F].pure(Some(value))
                case (newState, Action.Fork(value, takenBack)) =>
                  state = newState
                  unsafeTakeBack(takenBack)
                  Effect[F].pure(Some(value))
                case (newState, Action.ForkFinish(value, takenBack)) =>
                  state = newState
                  finished = true
                  unsafeTakeBack(takenBack)
                  Effect[F].pure(Some(value))
                case (newState, Action.TakeBack(takenBack)) =>
                  state = newState
                  unsafeTakeBack(takenBack)
                  pull()
                case (newState, Action.TakeBackFinish(takenBack)) =>
                  state = newState
                  finished = true
                  unsafeTakeBack(takenBack)
                  pull()
                case (newState, Action.TakeNext) =>
                  state = newState
                  pull()
                case (newState, Action.Finish) =>
                  state = newState
                  finished = true
                  Effect[F].pure(None)
              }
            case None => Effect[F].pure(None)
          }
        }
      }
      def cancel(): F[Unit] =
        self.cancel()
    }
  )
}

object Decoder {

  sealed trait Action[+From, +To]

  object Action {
    case class Push[+To](value: To) extends Action[Nothing, To]
    case class PushFinish[+To](value: To) extends Action[Nothing, To]
    case class Fork[+From, +To](value: To, takeBack: From) extends Action[From, To]
    case class ForkFinish[+From, +To](value: To, takeBack: From) extends Action[From, To]
    case class TakeBack[+From](value: From) extends Action[From, Nothing]
    case class TakeBackFinish[+From](value: From) extends Action[From, Nothing]
    case object TakeNext extends Action[Nothing, Nothing]
    case object Finish extends Action[Nothing, Nothing]
  }

  def apply[F[_]: Effect, A](upstream: Stream[F, A]): Decoder[F, A] =
    new Decoder(upstream)
}
