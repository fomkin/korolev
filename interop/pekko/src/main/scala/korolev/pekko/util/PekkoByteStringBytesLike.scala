package korolev.pekko.util

import org.apache.pekko.util.ByteString
import korolev.data.BytesLike

import java.nio.ByteBuffer
import java.nio.charset.{Charset, StandardCharsets}

final class PekkoByteStringBytesLike extends BytesLike[ByteString] {

  val empty: ByteString = ByteString.empty

  def ascii(s: String): ByteString = ByteString.fromString(s, StandardCharsets.US_ASCII)

  def utf8(s: String): ByteString = ByteString.fromString(s, StandardCharsets.UTF_8)

  def wrapArray(bytes: Array[Byte]): ByteString = ByteString.fromArrayUnsafe(bytes)

  def copyBuffer(buffer: ByteBuffer): ByteString = ByteString.fromByteBuffer(buffer)

  def copyToBuffer(b: ByteString, buffer: ByteBuffer): Int = b.copyToBuffer(buffer)

  def copyFromArray(bytes: Array[Byte]): ByteString = ByteString.fromArray(bytes)

  def copyFromArray(bytes: Array[Byte], offset: Int, size: Int): ByteString = ByteString.fromArray(bytes, offset, size)

  def copyToArray(value: ByteString, array: Array[Byte], sourceOffset: Int, targetOffset: Int, length: Int): Unit =
    value.drop(sourceOffset).copyToArray(array, targetOffset, length)

  def asAsciiString(bytes: ByteString): String =
    bytes.decodeString(StandardCharsets.US_ASCII)

  def asUtf8String(bytes: ByteString): String =
    bytes.decodeString(StandardCharsets.UTF_8)

  def asString(bytes: ByteString, charset: Charset): String =
    bytes.decodeString(charset)

  def asArray(bytes: ByteString): Array[Byte] =
    bytes.toArray

  def asBuffer(bytes: ByteString): ByteBuffer =
    bytes.asByteBuffer

  def eq(l: ByteString, r: ByteString): Boolean =
    l.equals(r)

  def get(bytes: ByteString, i: Long): Byte =
    bytes(i.toInt)

  def length(bytes: ByteString): Long =
    bytes.length.toLong

  def concat(left: ByteString, right: ByteString): ByteString =
    left.concat(right)

  def slice(bytes: ByteString, start: Long, end: Long): ByteString =
    bytes.slice(start.toInt, end.toInt)

  def mapWithIndex(bytes: ByteString, f: (Byte, Long) => Byte): ByteString = {
    var i = 0
    bytes.map { x =>
      val res = f(x, i)
      i += 1
      res
    }
  }

  def indexOf(where: ByteString, that: Byte): Long =
    where.indexOf(that)

  def indexOf(where: ByteString, that: Byte, from: Long): Long =
    where.indexOf(that, from.toInt)

  def lastIndexOf(where: ByteString, that: Byte): Long =
    where.lastIndexOf(that)

  def indexOfSlice(where: ByteString, that: ByteString): Long =
    where.indexOfSlice(that)

  def lastIndexOfSlice(where: ByteString, that: ByteString): Long =
    where.lastIndexOfSlice(that)

  def foreach(bytes: ByteString, f: Byte => Unit): Unit =
    bytes.foreach(f)
}
