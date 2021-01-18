package korolev

import java.nio.ByteBuffer
import java.nio.charset.Charset

import _root_.scodec.bits.ByteVector
import korolev.data.BytesLike

object scodec {

  implicit object ScodecByteVectorBytesLikeInstance extends BytesLike[ByteVector] {

    def empty: ByteVector = ByteVector.empty

    def ascii(s: String): ByteVector =
      ByteVector.encodeAscii(s).fold(throw _, identity)

    def utf8(s: String): ByteVector =
      ByteVector.encodeUtf8(s).fold(throw _, identity)

    def wrapArray(bytes: Array[Byte]): ByteVector =
      ByteVector(bytes)

    def copyBuffer(buffer: ByteBuffer): ByteVector =
      ByteVector(buffer)

    def copyFromArray(bytes: Array[Byte]): ByteVector =
      ByteVector(bytes)

    def copyFromArray(bytes: Array[Byte], offset: Int, size: Int): ByteVector =
      ByteVector(bytes, offset, size)

    def copyToArray(value: ByteVector, array: Array[Byte], sourceOffset: Int, targetOffset: Int, length: Int): Unit =
      value.copyToArray(array, targetOffset, sourceOffset, length)

    def asAsciiString(bytes: ByteVector): String =
      bytes.decodeAscii.fold(throw _, identity)

    def asUtf8String(bytes: ByteVector): String =
      bytes.decodeUtf8.fold(throw _, identity)

    def asString(bytes: ByteVector, charset: Charset): String =
      bytes.decodeString(charset).fold(throw _, identity)

    def asArray(bytes: ByteVector): Array[Byte] =
      bytes.toArray

    def asBuffer(bytes: ByteVector): ByteBuffer =
      bytes.toByteBuffer

    def eq(l: ByteVector, r: ByteVector): Boolean =
      l.equals(r)

    def get(bytes: ByteVector, i: Long): Byte =
      bytes.get(i)

    def length(bytes: ByteVector): Long =
      bytes.length

    def concat(left: ByteVector, right: ByteVector): ByteVector =
      left ++ right

    def slice(bytes: ByteVector, start: Long, end: Long): ByteVector =
      bytes.slice(start, end)

    def mapWithIndex(bytes: ByteVector, f: (Byte, Long) => Byte): ByteVector = {
      var i = 0
      bytes.map { x =>
        val res = f(x, i)
        i += 1
        res
      }
    }

    def foreach(bytes: ByteVector, f: Byte => Unit): Unit =
      bytes.foreach(f)

    def indexOf(where: ByteVector, that: Byte): Long =
      where.indexOfSlice(ByteVector(that))

    def indexOf(where: ByteVector, that: Byte, from: Long): Long =
      where.indexOfSlice(ByteVector(that), from)

    def lastIndexOf(where: ByteVector, that: Byte): Long =
      lastIndexOfSlice(where, ByteVector(that))

    def indexOfSlice(where: ByteVector, that: ByteVector): Long =
      where.indexOfSlice(that)

    def lastIndexOfSlice(where: ByteVector, that: ByteVector): Long = {
      @annotation.tailrec
      def go(b: ByteVector, idx: Long): Long =
        if (b.endsWith(that)) idx - that.length
        else if (b.isEmpty) -1
        else go(b.dropRight(1), idx - 1)
      go(where, where.length)
    }
  }
}
