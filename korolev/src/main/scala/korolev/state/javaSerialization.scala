package korolev.state

import java.io._

object javaSerialization {

  implicit def serializer[T]: StateSerializer[T] = new StateSerializer[T] {
    def serialize(value: T): Array[Byte] = {
      val byteStream = new ByteArrayOutputStream()
      val objectStream = new ObjectOutputStream(byteStream)
      try {
        objectStream.writeObject(value)
        byteStream.toByteArray
      }
      finally {
        objectStream.close()
        byteStream.close()
      }
    }
  }

  implicit def deserializer[T]: StateDeserializer[T] = new StateDeserializer[T] {
    def deserialize(data: Array[Byte]): Option[T] = {
      val byteStream = new ByteArrayInputStream(data)
      val objectStream = new ObjectInputStream(byteStream)

      try {
        val value = objectStream.readObject()
        val typed = value.asInstanceOf[T]
        Some(typed)
      }
      catch {
        case _:InvalidClassException =>
          // That means state type was changed
          None
      } finally {
        objectStream.close()
        byteStream.close()
      }
    }
  }

}
