/*
 * Copyright 2017-2020 Aleksey Fomkin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
