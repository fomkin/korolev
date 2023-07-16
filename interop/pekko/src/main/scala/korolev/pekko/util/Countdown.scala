package korolev.pekko.util

import korolev.effect.Effect

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

final class Countdown[F[_]: Effect] {

  private final val state = new AtomicReference(Countdown.State(None, 0))

  def value(): F[Long] = Effect[F].delay(unsafeValue)

  def add(n: Int): F[Unit] = Effect[F].delay(unsafeAdd(n))

  def decOrLock(): F[Unit] = Effect[F].promise[Unit] { cb =>
    @tailrec
    def aux(): Unit = {
      val ref = state.get()
      if (ref.n == 0) {
        val newValue = ref.copy(pending = Some(cb))
        if (!state.compareAndSet(ref, newValue)) {
          aux()
        }
      } else {
        val newValue = ref.copy(n = ref.n - 1)
        if (state.compareAndSet(ref, newValue)) {
          cb(Countdown.unitToken)
        } else {
          aux()
        }
      }
    }
    aux()
  }

  def unsafeAdd(x: Long): Unit = {
    // x should be positive
    if (x > 0) {
      @tailrec
      def aux(): Unit = {
        val ref = state.get()
        ref.pending match {
          case Some(cb) =>
            if (state.compareAndSet(ref, Countdown.State(pending = None, n = ref.n + x - 1))) {
              cb(Countdown.unitToken)
            } else {
              aux()
            }
          case None =>
            if (!state.compareAndSet(ref, ref.copy(n = ref.n + x))) {
              aux()
            }
        }
      }
      aux()
    }
  }

  def unsafeValue: Long = state.get().n
}

object Countdown {
  val unitToken = Right(())
  case class State(pending: Option[Effect.Promise[Unit]], n: Long)
}
