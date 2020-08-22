package korolev.data

object syntax {

  implicit final class BytesLikeOps[T](bytes: T)(implicit instance: BytesLike[T]) {
    def apply(i: Int): Int = apply(i.toLong)
    def apply(i: Long): Int = instance.get(bytes, i)
    def length: Long = instance.length(bytes)
    def isEmpty: Boolean = length == 0
    def concat(right: T): T = BytesLike[T].concat(bytes, right)
    def ++(right: T): T = BytesLike[T].concat(bytes, right)
    def slice(from: Long, until: Long): T = BytesLike[T].slice(bytes, from, until)
    def slice(from: Long): T = BytesLike[T].slice(bytes, from, length)
    def mapI(f: (Long, Byte) => Byte): T = BytesLike[T].mapI(bytes, f)
    def indexOf(elem: Byte): Long = BytesLike[T].indexOf(bytes, elem)
    def indexOf(elem: Byte, from: Long): Long = BytesLike[T].indexOf(bytes, elem, from)
    def lastIndexOf(elem: Byte): Long = BytesLike[T].lastIndexOf(bytes, elem)
    def indexOfSlice(elem: T): Long = BytesLike[T].indexOfSlice(bytes, elem)
    def lastIndexOfSlice(elem: T): Long = BytesLike[T].lastIndexOfSlice(bytes, elem)
    def asUtf8String: String = instance.asUtf8String(bytes)
    def asAsciiString: String = instance.asAsciiString(bytes)
    def asArray: Array[Byte] = instance.asArray(bytes)
    def as[T2: BytesLike]: T2 = instance.as[T2](bytes)
  }
}
