package korolev.data

import java.nio.ByteBuffer
import java.nio.charset.{Charset, StandardCharsets}

/**
 * Facade for any BytesLike structure
 */
sealed trait Bytes {
  def apply(i: Int): Byte
  def apply(i: Long): Byte
  def mapWithIndex(f: (Byte, Long) => Byte): Bytes
  def foreach(f: Byte => Unit): Unit
  def length: Long
  def concat(right: Bytes): Bytes
  def ++(right: Bytes): Bytes
  def as[T2: BytesLike]: T2
  def asArray: Array[Byte]
  def asBuffer: ByteBuffer
  def asHexString: String
  def asAsciiString: String
  def asUtf8String: String
  def asString(charset: Charset): String
  def slice(from: Long, until: Long): Bytes
  def indexOf(elem: Byte): Long
  def indexOf(elem: Byte, start: Long): Long
  def lastIndexOf(elem: Byte): Long
  def indexOfSlice(elem: Bytes): Long
  def lastIndexOfSlice(elem: Bytes): Long
  def copyToBuffer(buffer: ByteBuffer): Int
}

object Bytes {

  def apply[T: BytesLike](xs: Int*): Bytes =
    Bytes.wrap(BytesLike[T].wrapArray(xs.toArray.map(_.toByte)))
  val empty: Bytes = Bytes.wrap(new Array[Byte](0))

  def wrap[T: BytesLike](that: T): Bytes = new Bytes {
    def apply(i: Int): Byte = BytesLike[T].get(that, i)
    def apply(i: Long): Byte = BytesLike[T].get(that, i)
    def concat(right: Bytes): Bytes = {
      if (BytesLike[T].length(that) == 0) {
        right
      } else {
        Bytes.wrap(BytesLike[T].concat(that, right.as[T]))
      }
    }
    def ++(right: Bytes): Bytes = concat(right)
    def as[T2: BytesLike]: T2 = BytesLike[T].as[T2](that)
    def asAsciiString: String = BytesLike[T].asAsciiString(that)
    def asUtf8String: String = BytesLike[T].asUtf8String(that)
    def asString(charset: Charset): String = BytesLike[T].asString(that, charset)
    def slice(from: Long, until: Long): Bytes = Bytes.wrap(BytesLike[T].slice(that, from, until))
    def indexOf(elem: Byte): Long = BytesLike[T].indexOf(that, elem)
    def lastIndexOf(elem: Byte): Long = BytesLike[T].lastIndexOf(that, elem)
    def indexOfSlice(elem: Bytes): Long = BytesLike[T].indexOfSlice(that, elem.as[T])
    def lastIndexOfSlice(elem: Bytes): Long = BytesLike[T].lastIndexOfSlice(that, elem.as[T])
    def length: Long = BytesLike[T].length(that)
    def asArray: Array[Byte] = BytesLike[T].asArray(that)
    def asBuffer: ByteBuffer = BytesLike[T].asBuffer(that)
    def asHexString: String = BytesLike[T].asHexString(that)
    def indexOf(elem: Byte, start: Long): Long = BytesLike[T].indexOf(that, elem, start)
    def mapWithIndex(f: (Byte, Long) => Byte): Bytes = Bytes.wrap(BytesLike[T].mapWithIndex(that, f))
    def foreach(f: Byte => Unit): Unit = BytesLike[T].foreach(that, f)
    def copyToBuffer(buffer: ByteBuffer): Int = BytesLike[T].copyToBuffer(that, buffer)

    override def equals(obj: Any): Boolean =
      if (obj.isInstanceOf[Bytes]) BytesLike[T].eq(that, obj.asInstanceOf[Bytes].as[T])
      else false
    override def toString: String = s"Bytes(${BytesLike[T].asHexString(that)})"
  }

  implicit object BytesBytesLikeInstance extends BytesLike[Bytes] {

    def empty: Bytes = Bytes.empty

    def ascii(s: String): Bytes = Bytes.wrap(s.getBytes(StandardCharsets.US_ASCII))

    def utf8(s: String): Bytes = Bytes.wrap(s.getBytes(StandardCharsets.UTF_8))

    def wrapArray(bytes: Array[Byte]): Bytes = Bytes.wrap(bytes)

    def copyBuffer(buffer: ByteBuffer): Bytes = {
      val array = new Array[Byte](buffer.remaining())
      buffer.get(array)
      wrapArray(array)
    }

    def copyFromArray(bytes: Array[Byte]): Bytes =
      wrapArray(bytes.clone())

    def copyToBuffer(b: Bytes, buffer: ByteBuffer): Int =
      b.copyToBuffer(buffer)

    def copyFromArray(bytes: Array[Byte], offset: Int, size: Int): Bytes =
      wrapArray(bytes.slice(offset.toInt, (offset + size).toInt))

    def copyToArray(value: Bytes, array: Array[Byte], sourceOffset: Int, targetOffset: Int, length: Int): Unit =
      System.arraycopy(value, sourceOffset, array, targetOffset, length)

    def asAsciiString(bytes: Bytes): String =
      bytes.asAsciiString

    def asUtf8String(bytes: Bytes): String =
      bytes.asUtf8String

    def asString(bytes: Bytes, charset: Charset): String =
      bytes.asString(charset)

    def asArray(bytes: Bytes): Array[Byte] =
      bytes.asArray

    def asBuffer(bytes: Bytes): ByteBuffer =
      bytes.asBuffer

    def eq(l: Bytes, r: Bytes): Boolean =
      l == r

    def get(bytes: Bytes, i: Long): Byte =
      bytes(i)

    def length(bytes: Bytes): Long =
      bytes.length

    def concat(left: Bytes, right: Bytes): Bytes =
      left ++ right

    def slice(bytes: Bytes, start: Long, end: Long): Bytes =
      bytes.slice(start, end)

    def mapWithIndex(bytes: Bytes, f: (Byte, Long) => Byte): Bytes =
      bytes.mapWithIndex(f)

    def foreach(bytes: Bytes, f: Byte => Unit): Unit =
      bytes.foreach(f)

    def indexOf(where: Bytes, that: Byte): Long =
      where.indexOf(that)

    def indexOf(where: Bytes, that: Byte, from: Long): Long = 
      where.indexOf(that, from)

    def lastIndexOf(where: Bytes, that: Byte): Long =
      where.lastIndexOf(that)

    def indexOfSlice(where: Bytes, that: Bytes): Long =
      where.indexOfSlice(that)

    def lastIndexOfSlice(where: Bytes, that: Bytes): Long =
      where.lastIndexOfSlice(that)
  }
}