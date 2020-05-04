package korolev.effect

import java.util.concurrent.atomic.AtomicReference

final class Var[F[_]: Effect, T](initialValue: T) {
  private val casRef = new AtomicReference[T](initialValue)
  def set(value: T): F[Unit] =
    Effect[F].delay(casRef.set(value))
  def get: F[T] =
    Effect[F].delay(casRef.get)
  def compareAndSet(expected: T, value: T): F[Boolean] =
    Effect[F].delay(casRef.compareAndSet(expected, value))
}

object Var {
  def apply[F[_]: Effect, T](initialValue: T): Var[F, T] =
    new Var(initialValue)
}
