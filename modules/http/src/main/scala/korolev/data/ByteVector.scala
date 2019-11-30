package korolev.data

import java.nio.ByteBuffer
import java.nio.charset.{Charset, StandardCharsets}

import scala.annotation.{switch, tailrec}
import scala.collection.GenSeq

sealed trait ByteVector {

  import ByteVector._

  def length: Long

  def apply(i: Long): Byte = (unsafeGetByte(i): @switch) match {
    case UnsafeGetOutOfBounds => throw new ArrayIndexOutOfBoundsException(i.toInt)
    case result => result.toByte
  }

  def get(i: Long): Option[Byte] = (unsafeGetByte(i): @switch) match {
    case UnsafeGetOutOfBounds => None
    case result => Some(result.toByte)
  }

  @tailrec private def unsafeGetByte(i: Long): Int = this match {
    case _ if i < 0 => UnsafeGetOutOfBounds
    case arr: Arr if i < arr.array.length.toLong => arr.array(i.toInt).toInt
    case concat: Concat if i < concat.lhs.length => concat.lhs.unsafeGetByte(i)
    case concat: Concat =>
      val ri = i - concat.lhs.length
      if (ri < concat.rhs.length) concat.rhs.unsafeGetByte(ri)
      else UnsafeGetOutOfBounds
    case slice: Slice if (i + slice.from) < slice.until =>
      slice.bv.unsafeGetByte(i + slice.from)
    case _ => UnsafeGetOutOfBounds
  }

  def readByte(i: Long): Byte = apply(i)

  def readShort(i: Long): Short = ((readByte(i) & 0xFF) << 8 | (readByte(i + 1) & 0xFF)).toShort

  def readInt(i: Long): Int = ((readShort(i) & 0xFFFF) << 16) | (readShort(i + 2) & 0xFFFF)

  def readLong(i: Long): Long = ((readInt(i) & 0xFFFFFFFFL) << 32) | (readInt(i + 4) & 0xFFFFFFFFL)

  private def searchIndex(i: Long, reversed: Boolean = false)(f: (Long, Array[Byte]) => Long): Long =
    this match {
      case Empty => -1
      case arr: Arr => f(i, arr.array)
      case concat: Concat if reversed =>
        val j = concat.rhs.searchIndex(i)(f)
        if (j > -1) j else concat.lhs.searchIndex(concat.rhs.length)(f)
      case concat: Concat =>
        val j = concat.lhs.searchIndex(i)(f)
        if (j > -1) j else concat.rhs.searchIndex(concat.lhs.length)(f)
      case slice: Slice =>
        val j = slice.bv.searchIndex(i)(f)
        if (j >= slice.from && j < slice.until) j - slice.from else -1
    }

  def indexOf(that: Byte): Long =
    indexOf(that, 0L)

  def indexOf(that: Byte, from: Long): Long =
    searchIndex(0) { (i, x) =>
      val j = x.indexOf(that, (from - i).toInt)
      if (j > -1) i + j
      else -1
    }

  def lastIndexOfSlice(that: GenSeq[Byte]): Long =
    searchIndex(0, reversed = true) { (i, x) =>
      val j = x.lastIndexOfSlice(that)
      if (j > -1) i + j
      else -1
    }

//  def indexOfSlice(that: GenSeq[Byte]): Long =
//    searchIndex(0) { (i, x) =>
//      val j = x.indexOfSlice(that)
//      if (j > -1) i + j
//      else -1
//    }

  def indexOfThat(that: ByteVector): Long = {
    val l = that.length - 1
    var i = 0
    var j = 0
    var k = that(j)
    var f = -1
    this.forall { x =>
      val res = if (x == k) {
        if (j == l) {
          false
        } else {
          if (j == 0)
            f = i
          j += 1
          k = that(j)
          true
        }
      } else if (j > 0) {
        f = -1
        j = 0
        k = that(j)
        true
      } else {
        true
      }
      i += 1
      res
    }
    f
  }

  // TODO what if bytevector large than array?
  def mapI(f: (Byte, Int) => Byte): ByteVector =
    new Arr(mkArray(f))

  def forall(f: Byte => Boolean): Boolean = this match {
    case Empty => true
    case arr: Arr => arr.array.forall(f)
    case concat: Concat =>
      if (concat.lhs.forall(f)) {
        concat.rhs.forall(f)
      } else {
        false
      }
    case slice: Slice =>
      var i = 0L
      slice.bv.forall { x =>
        val res = if (i >= slice.from && i < slice.until) {
          f(x)
        } else {
          true
        }
        i = i + 1
        res
      }
  }

  def foreach(f: Byte => Unit): Unit = this match {
    case Empty => ()
    case arr: Arr => arr.array.foreach(f)
    case concat: Concat =>
      concat.lhs.foreach(f)
      concat.rhs.foreach(f)
    case slice: Slice =>
      var i = 0L
      slice.bv.foreach { x =>
        if (i >= slice.from && i < slice.until) {
          f(x)
        }
        i = i + 1
      }
  }

  def mkArray(f: (Byte, Int) => Byte): Array[Byte] = {
    val array = new Array[Byte](length.toInt)
    var i = 0
    foreach { x =>
      array(i) = f(x, i)
      i = i + 1
    }
    array
  }

  def mkArray: Array[Byte] =
    mkArray((x, _) => x)

  def mkBuffer(buffer: ByteBuffer): Unit =
    foreach { i =>
      val _ = buffer.put(i)
    }

  def mkBuffer: ByteBuffer = {
    val buffer = ByteBuffer.allocate(length.toInt)
    mkBuffer(buffer)
    buffer
  }

  def slice(from: Long, until: Long): ByteVector =
    if (from < 0) throw new IndexOutOfBoundsException("Start of slice can't be less than zero")
    else if (until - from <= 0) ByteVector.Empty
    else new Slice(this, from, until) // TODO optimize me. Nested slices could be inlined

  def slice(from: Long): ByteVector =
    slice(from, length)

  def utf8String: String =
    new String(mkBuffer.array(), StandardCharsets.UTF_8)

  def asciiString: String = {
    val builder = new StringBuilder
    foreach { byte =>
      val _ = builder.append(byte.toChar)
    }
    builder.mkString
  }

  def ++(bv: ByteVector): ByteVector =
    if (bv == Empty) this
    else new Concat(this, bv)

  def :+(array: Array[Byte]): ByteVector =
    new Concat(this, new Arr(array))

  def +:(array: Array[Byte]): ByteVector =
    new Concat(new Arr(array), this)


  override lazy val toString: String = {
    val builder = new StringBuilder()
    val _ = builder.append("ByteVector(")
    foreach { x =>
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
    }
    builder
      .deleteCharAt(builder.length - 1)
      .append(')')
      .mkString
  }

  def describeStructure: String = {
    def aux(sb: StringBuilder, indent: Int, node: ByteVector): StringBuilder = node match {
      case Empty =>
        sb.append(" " * indent)
          .append("()\n")
      case arr: Arr =>
        sb.append(" " * indent)
        .append(s"Array[length=${arr.length}](...)\n")
      case concat: Concat =>
        val sb2 = sb
          .append(" " * indent)
          .append(s"Concat[length=${concat.length}](\n")
        aux(aux(sb2, indent + 2, concat.lhs), indent + 2, concat.rhs)
          .append(" " * indent)
          .append(")\n")
      case slice: Slice =>
        val sb2 = sb
          .append(" " * indent)
          .append(s"Slice[length=${slice.length},from=${slice.from},until=${slice.until}](\n")
        aux(sb2, indent + 2, slice.bv)
          .append(" " * indent)
          .append(")\n")
    }
    val sb = new StringBuilder()
    aux(sb, 0, this)
    sb.mkString
  }

  override def equals(that: Any): Boolean = that match {
    case bv: ByteVector => bv.hashCode == this.hashCode
    case _ => false
  }

  final override lazy val hashCode: Int = {
    import scala.util.hashing.MurmurHash3._
    var h = 0x221fe95c // seed
    foreach { x => h = mix(h, x.toInt) }
    // TODO what if length > maxint?
    finalizeHash(h, length.toInt)
  }
}

object ByteVector {

  private final val UnsafeGetOutOfBounds = -1000

  final val CRLF = ByteVector.ascii("\r\n")

  class Arr(val array: Array[Byte]) extends ByteVector {
    lazy val length: Long = array.length.toLong
  }

  class Concat(val lhs: ByteVector, val rhs: ByteVector) extends ByteVector {
    lazy val length: Long = lhs.length + rhs.length
  }

  class Slice(val bv: ByteVector, val from: Long, val until: Long) extends ByteVector {
    lazy val length: Long =  {
      if (until > -1) until - from
      else bv.length - from
    }
  }

  object Empty extends ByteVector {
    val length = 0
    override lazy val toString = "ByteVector.Empty"
    override def equals(that: Any): Boolean = that match {
      case bv: ByteVector => (bv eq Empty) || bv.length == 0
      case _ => false
    }
    override def ++(bv: ByteVector): ByteVector = bv
    override def :+(array: Array[Byte]): ByteVector = new Arr(array)
    override def +:(array: Array[Byte]): ByteVector = new Arr(array)
  }

  def fromBuffer(buffer: ByteBuffer): ByteVector = {
    val array = new Array[Byte](buffer.remaining())
    buffer.get(array)
    new Arr(array)
  }

  def ascii(s: String): ByteVector = new Arr(s.getBytes(StandardCharsets.US_ASCII))
  def utf8(s: String): ByteVector = new Arr(s.getBytes(StandardCharsets.UTF_8))
  def fill(length: Int)(f: Int => Byte): ByteVector =
    new Arr((0 until length).map(f).toArray)
  def apply(xs: Int*): Arr = new Arr(xs.map(_.toByte).toArray)
  def apply(array: Array[Byte]): ByteVector = new Arr(array)
  def apply(s: String, charset: Charset): ByteVector = new Arr(s.getBytes(charset))
  val empty: ByteVector = Empty
}