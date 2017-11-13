package korolev.state

trait StateDeserializer[T] {
  def deserialize(data: Array[Byte]): Option[T]
}
