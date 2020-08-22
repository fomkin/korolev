package korolev.data

import java.nio.ByteBuffer

trait BytesLike[T] {
  // Create
  def empty: T
  def ascii(s: String): T
  def wrapArray(bytes: Array[Byte]): T
  def copyBuffer(buffer: ByteBuffer): T
  def copyFromArray(bytes: Array[Byte]): T
  def copyFromArray(bytes: Array[Byte], offset: Long, size: Long): T
  // Convert
  def copyToArray(value: T, array: Array[Byte], sourceOffset: Int, targetOffset: Int, length: Int): Unit
  def asAsciiString(bytes: T): String
  def asUtf8String(bytes: T): String
  def asArray(bytes: T): Array[Byte]
  def asBuffer(bytes: T): ByteBuffer
  def as[T2: BytesLike](that: T): T2 =
    if (BytesLike[T2] == this) that.asInstanceOf[T2]
    else BytesLike[T2].wrapArray(asArray(that))
  // Use
  def get(bytes: T, i: Long): Byte
  def length(bytes: T): Long
  def concat(left: T, right: T): T
  def slice(bytes: T, start: Long, end: Long): T
  def mapI(bytes: T, f: (Long, Byte) => Byte): T

  // Search
  def indexOf(where: T, that: Byte): Long
  def indexOf(where: T, that: Byte, from: Long): Long
  def lastIndexOf(where: T, that: Byte): Long
  def indexOfSlice(where: T, that: T): Long
  def lastIndexOfSlice(where: T, that: T): Long
}

object BytesLike {

  def apply[T: BytesLike]: BytesLike[T] = implicitly[BytesLike[T]]

  implicit object ArrayBytesLikeInstance extends BytesLike[Array[Byte]] {

    val empty: Array[Byte] =
      Array()

    def copyFromArray(bytes: Array[Byte]): Array[Byte] =
      bytes.clone()

    def copyFromArray(bytes: Array[Byte], offset: Long, size: Long): Array[Byte] =
      bytes.slice(offset.toInt, (offset + size).toInt)

    def copyToArray(value: Array[Byte], array: Array[Byte], sourceOffset: Int, targetOffset: Int, length: Int): Unit =
      System.arraycopy(value, sourceOffset, array, targetOffset, length)

    def wrapArray(bytes: Array[Byte]): Array[Byte] =
      bytes

    def copyBuffer(buffer: ByteBuffer): Array[Byte] =
      buffer.array()

    def asArray(bytes: Array[Byte]): Array[Byte] =
      bytes

    def asBuffer(bytes: Array[Byte]): ByteBuffer =
      ByteBuffer.wrap(bytes)

    def get(bytes: Array[Byte], i: Long): Int =
      bytes(i.toInt)

    def length(bytes: Array[Byte]): Long =
      bytes.length.toLong

    def concat(left: Array[Byte], right: Array[Byte]): Array[Byte] =
      left ++ right

    def slice(bytes: Array[Byte], start: Long, end: Long): Array[Byte] =
      bytes.slice(start.toInt, end.toInt)

    def indexOf(where: Array[Byte], that: Byte): Long =
      where.indexOf(that)

    def lastIndexOf(where: Array[Byte], that: Byte): Long =
      where.lastIndexOf(that)

    def indexOfSlice(where: Array[Byte], that: Array[Byte]): Long =
      where.indexOfSlice(that)

    def lastIndexOfSlice(where: Array[Byte], that: Array[Byte]): Long =
      where.lastIndexOfSlice(that)
  }
}
