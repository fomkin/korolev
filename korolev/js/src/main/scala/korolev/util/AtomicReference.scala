package korolev.util

/** Scala wrapper around AtomicReference
  *
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
class AtomicReference[T](initialValue: T) {

  private var value = initialValue

  def apply(): T = value

  def transform(f: T => T): T = {
    value = f(value)
    value
  }

  def update(newValue: T): Unit = value = newValue
}

object AtomicReference {
  def apply[T](initialValue: T): AtomicReference[T] =
    new AtomicReference(initialValue)
}
