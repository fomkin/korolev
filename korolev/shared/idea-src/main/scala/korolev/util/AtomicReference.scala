package korolev.util

import java.util.concurrent.atomic.{AtomicReference => JavaAtomicReference}
import java.util.function.UnaryOperator

/** Scala wrapper around `AtomicReference`. */
class AtomicReference[T](initialValue: T) {

  private val ref = new JavaAtomicReference[T](initialValue)

  def apply(): T = ref.get()

  def transform(f: T => T): T = ref updateAndGet {
    new UnaryOperator[T] {
      def apply(t: T): T = f(t)
    }
  }

  def update(newValue: T): Unit = ref.set(newValue)
}

object AtomicReference {
  def apply[T](initialValue: T): AtomicReference[T] =
    new AtomicReference(initialValue)
}
