package korolev.data
import java.nio.ByteBuffer

/**
 * Facade for any BytesLike structure
 */
sealed trait Bytes {
  def apply(i: Int): Int
  def apply(i: Long): Int
  def concat(right: Bytes): Bytes
  def ++(right: Bytes): Bytes
  def as[T2: BytesLike]: T2
  def slice(from: Long, until: Long): Bytes
  def indexOf(elem: Byte): Long
  def lastIndexOf(elem: Byte): Long
  def indexOfSlice(elem: Bytes): Long
  def lastIndexOfSlice(elem: Bytes): Long
}

object Bytes {
  def wrap[T: BytesLike](that: T): Bytes = new Bytes {
    def apply(i: Int): Int = BytesLike[T].get(that, i)
    def apply(i: Long): Int = BytesLike[T].get(that, i)
    def concat(right: Bytes): Bytes = Bytes.wrap(BytesLike[T].concat(that, right.as[T]))
    def ++(right: Bytes): Bytes = concat(right)
    def as[T2: BytesLike]: T2 = BytesLike[T].as[T2](that)
    def slice(from: Long, until: Long): Bytes = Bytes.wrap(BytesLike[T].slice(that, from, until))
    def indexOf(elem: Byte): Long = BytesLike[T].indexOf(that, elem)
    def lastIndexOf(elem: Byte): Long = BytesLike[T].lastIndexOf(that, elem)
    def indexOfSlice(elem: Bytes): Long = BytesLike[T].indexOfSlice(that, elem.as[T])
    def lastIndexOfSlice(elem: Bytes): Long = BytesLike[T].lastIndexOfSlice(that, elem.as[T])
  }

  implicit object BytesBytesLikeInstance extends BytesLike[Bytes] {
    override def empty: Bytes = ???
    override def wrapArray(bytes: Array[Byte]): Bytes = ???
    override def copyBuffer(buffer: ByteBuffer): Bytes = ???

    override def copyFromArray(bytes: Array[Byte]): Bytes = ???

    override def copyFromArray(bytes: Array[Byte], offset: Long, size: Long): Bytes = ???

    override def copyToArray(value: Bytes, array: Array[Byte], sourceOffset: Int, targetOffset: Int, length: Int): Unit = ???

    override def asArray(bytes: Bytes): Array[Byte] = ???

    override def asBuffer(bytes: Bytes): ByteBuffer = ???

    override def get(bytes: Bytes, i: Long): Int = ???

    override def length(bytes: Bytes): Long = ???

    override def concat(left: Bytes, right: Bytes): Bytes = ???

    override def slice(bytes: Bytes, start: Long, end: Long): Bytes = ???

    override def indexOf(where: Bytes, that: Byte): Long = ???

    override def lastIndexOf(where: Bytes, that: Byte): Long = ???

    override def indexOfSlice(where: Bytes, that: Bytes): Long = ???

    override def lastIndexOfSlice(where: Bytes, that: Bytes): Long = ???
  }
}