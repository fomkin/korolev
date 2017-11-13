package korolev.state

trait StateSerializer[T] {
  def serialize(value: T): Array[Byte]
}
