package korolev.data

import org.scalatest.{FlatSpec, Matchers}

class ByteVectorSpec extends FlatSpec with Matchers {

  final val concatSample1 = ByteVector("abc".getBytes) :+ "def".getBytes
  final val concatSample2 = ("---".getBytes +: concatSample1 :+ "+++".getBytes)
  final val sliceSample1 = concatSample2.slice(3, 9)
  final val sliceSample2 = (sliceSample1 :+ "ghj".getBytes).slice(3, 9)
  final val sliceSample3 = sliceSample1.slice(0, 3)
  final val binarySample1 = ByteVector(0x01, 0x23, 0x45, 0x67, 0x89, 0x10, 0x11, 0x12)

  "asciiString" should "encode concatenated byte vector #1" in {
    concatSample1.asciiString shouldEqual "abcdef"
  }

  it should "encode concatenated byte vector #2" in {
    concatSample2.asciiString shouldEqual "---abcdef+++"
  }

  it should "encode sliced byte vector #1" in {
    sliceSample1.asciiString shouldEqual "abcdef"
  }

  it should "encode sliced byte vector #2" in {
    sliceSample2.asciiString shouldEqual "defghj"
  }

  it should "encode sliced byte vector #3" in {
    sliceSample3.asciiString shouldEqual "abc"
  }

  "length" should "be calculated correctly for concatenated byte vector #1" in {
    concatSample1.length shouldEqual 6
  }

  it should "be calculated correctly for sliced byte vector #1" in {
    sliceSample1.length shouldEqual 6
  }

  it should "be calculated correctly for sliced byte vector #2" in {
    sliceSample2.length shouldEqual 6
  }

  it should "be calculated correctly for sliced byte vector #3" in {
    sliceSample3.length shouldEqual 3
  }

  "get" should "return bytes matched to index at concatenated byte vector #1" in {
    concatSample1.get(-1) shouldEqual None
    concatSample1.get(0) shouldEqual Some('a')
    concatSample1.get(1) shouldEqual Some('b')
    concatSample1.get(2) shouldEqual Some('c')
    concatSample1.get(3) shouldEqual Some('d')
    concatSample1.get(4) shouldEqual Some('e')
    concatSample1.get(5) shouldEqual Some('f')
  }

  it should "return bytes matched to index at sliced byte vector #3" in {
    sliceSample3.get(-1) shouldEqual None
    sliceSample3.get(0) shouldEqual Some('a')
    sliceSample3.get(1) shouldEqual Some('b')
    sliceSample3.get(2) shouldEqual Some('c')
    sliceSample3.get(3) shouldEqual None
  }

  "readShort" should "return correct number #1" in {
    binarySample1.readShort(2) shouldEqual 0x4567
  }

  it should "return correct number #2" in {
    binarySample1.readShort(3) shouldEqual 0x6789
  }

  "readInt" should "return correct number #1" in {
    binarySample1.readInt(1).toHexString shouldEqual "23456789"
  }

  "readLong" should "return correct number #1" in {
    binarySample1.readLong(0) shouldEqual 0x0123456789101112L
  }

  "==" should "find equality of vectors with same data but different structure" in {
    concatSample1 shouldEqual sliceSample1
  }

  it should "find inequality of vectors with different data" in {
    assert(concatSample1 != concatSample2)
  }

  "indexOfThat" should "find slice in sliced vector" in {
    val sample = (ByteVector.ascii("xx") ++ ByteVector.ascii("x ") ++ ByteVector.ascii("hello\r\nworld")).slice(4)
    val result = sample.indexOfThat(ByteVector.ascii("\r\n"))
    assert(result == 5)
  }

  it should "return -1 if subslice is not found" in {
    val sample = (ByteVector.ascii("xx") ++ ByteVector.ascii("x ") ++ ByteVector.ascii("hello world")).slice(4)
    val result = sample.indexOfThat(ByteVector.ascii("\r\n"))
    assert(result == -1)
  }
}
