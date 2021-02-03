package korolev.data

object BytesReader {

  def readByte[T: BytesLike](bytes: T, i: Long): Byte =
    BytesLike[T].get(bytes, i)

  def readShort[T: BytesLike](bytes: T, i: Long): Short =
    ((readByte(bytes, i) & 0xFF) << 8 | (readByte(bytes, i + 1) & 0xFF)).toShort

  def readInt[T: BytesLike](bytes: T, i: Long): Int =
    ((readShort(bytes, i) & 0xFFFF) << 16) | (readShort(bytes, i + 2) & 0xFFFF)

  def readLong[T: BytesLike](bytes: T, i: Long): Long =
    ((readInt(bytes, i) & 0xFFFFFFFFL) << 32) | (readInt(bytes, i + 4) & 0xFFFFFFFFL)
}
