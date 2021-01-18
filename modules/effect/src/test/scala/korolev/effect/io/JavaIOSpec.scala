package korolev.effect.io

import java.io.ByteArrayInputStream

import org.scalatest.{AsyncFlatSpec, Matchers}

class JavaIOSpec extends AsyncFlatSpec with Matchers {

  final val inputStream1Length = 239978
  final val inputStream1 = new ByteArrayInputStream(Array.fill[Byte](inputStream1Length)(1))

  "JavaIO.fromInputStream" should "return exactly same bytes as contains in InputStream" in {
    JavaIO.fromInputStream(inputStream1).flatMap(_.fold(Array.empty[Byte])(_ ++ _)).map { bytes =>
      bytes.length shouldEqual inputStream1Length
    }
  }
}
