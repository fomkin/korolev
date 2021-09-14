package korolev.data

import java.nio.ByteBuffer
import java.nio.charset.{Charset, StandardCharsets}

trait BytesLike[T] {
  // Create
  def empty: T
  def ascii(s: String): T
  def utf8(s: String): T
  def wrapArray(bytes: Array[Byte]): T
  def copyBuffer(buffer: ByteBuffer): T
  def copyToBuffer(b: T, buffer: ByteBuffer): Int
  def copyFromArray(bytes: Array[Byte]): T
  def copyFromArray(bytes: Array[Byte], offset: Int, size: Int): T
  // Convert
  def copyToArray(value: T, array: Array[Byte], sourceOffset: Int, targetOffset: Int, length: Int): Unit
  def asAsciiString(bytes: T): String
  def asUtf8String(bytes: T): String
  def asString(bytes: T, charset: Charset): String
  def asArray(bytes: T): Array[Byte]
  def asBuffer(bytes: T): ByteBuffer
  def asHexString(bytes: T): String = {
    val builder = new StringBuilder()
    foreach(bytes, { x =>
      val s = (x & 0xFF).toHexString
      val _ = if (s.length == 2) {
        builder
          .append(s)
          .append(' ')
      } else {
        builder
          .append('0')
          .append(s)
          .append(' ')
      }
    })
    builder
      .deleteCharAt(builder.length - 1)
      .mkString
  }

  def as[T2: BytesLike](that: T): T2 =
    if (BytesLike[T2] == this) that.asInstanceOf[T2]
    else BytesLike[T2].wrapArray(asArray(that))
  // Use
  def eq(l: T, r: T): Boolean
  def get(bytes: T, i: Long): Byte
  def length(bytes: T): Long
  def concat(left: T, right: T): T
  def slice(bytes: T, start: Long, end: Long): T
  // Scan
  def mapWithIndex(bytes: T, f: (Byte, Long) => Byte): T
  def foreach(bytes: T, f: Byte => Unit): Unit
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
    
    def ascii(s: String): Array[Byte] =
      s.getBytes(StandardCharsets.US_ASCII)

    def utf8(s: String): Array[Byte] =
      s.getBytes(StandardCharsets.UTF_8)

    def asAsciiString(bytes: Array[Byte]): String =
      new String(bytes, StandardCharsets.US_ASCII)

    def asUtf8String(bytes: Array[Byte]): String =
      new String(bytes, StandardCharsets.UTF_8)

    def asString(bytes: Array[Byte], charset: Charset): String =
      new String(bytes, StandardCharsets.UTF_8)

    def mapWithIndex(bytes: Array[Byte], f: (Byte, Long) => Byte): Array[Byte] =
      // TODO optimize me
      bytes.zipWithIndex.map {
        case (x, i) => 
          f(x, i.toLong)
      }

    def foreach(bytes: Array[Byte], f: Byte => Unit): Unit =
      bytes.foreach(f)

    def indexOf(where: Array[Byte], that: Byte, from: Long): Long =
      where.indexOf(that, from.toInt)

    val empty: Array[Byte] =
      Array()

    def copyFromArray(bytes: Array[Byte]): Array[Byte] =
      bytes.clone()

    def copyFromArray(bytes: Array[Byte], offset: Int, size: Int): Array[Byte] =
      bytes.slice(offset.toInt, (offset + size))

    def copyToArray(value: Array[Byte], array: Array[Byte], sourceOffset: Int, targetOffset: Int, length: Int): Unit =
      System.arraycopy(value, sourceOffset, array, targetOffset, length)

    def wrapArray(bytes: Array[Byte]): Array[Byte] =
      bytes

    def copyBuffer(buffer: ByteBuffer): Array[Byte] = {
      val array = new Array[Byte](buffer.remaining())
      buffer.get(array)
      array
    }

    def copyToBuffer(b: Array[Byte], buffer: ByteBuffer): Int = {
      val r = buffer.remaining()
      buffer.put(b)
      r
    }

    def asArray(bytes: Array[Byte]): Array[Byte] =
      bytes

    def asBuffer(bytes: Array[Byte]): ByteBuffer =
      ByteBuffer.wrap(bytes)

    def eq(l: Array[Byte], r: Array[Byte]): Boolean =
      l sameElements r

    def get(bytes: Array[Byte], i: Long): Byte =
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
