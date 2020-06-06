package korolev

import org.scalatest.{FlatSpec, Matchers}

class RouterSpec extends FlatSpec with Matchers {

  import korolev.Router._

  "Korolev Router" should "correct work with Root" in {
    val path: Path = Root / "api"

    path.mkString should be("/api")
  }

  "Korolev Router" should "correct work without Root" in {
    val path: Path = Segment("api")

    path.mkString should be("/api")
  }

  "Korolev Router" should "correct concatenate two not Root path" in {
    val api: Path = Segment("api")
    val v2: Path = Segment("v2")
    val result = api / v2

    result.mkString should be("/api/v2")
  }

  "Korolev Router" should "correct concatenate mixed path" in {
    val api: Path = Segment("api")
    val v2: Path = Segment("v2")
    val result = api / "en" / v2

    result.mkString should be("/api/en/v2")
  }

  "Korolev Router" should "correct do startWith with Root" in {
    val path: Path = Root / "api" / "v2"

    path.startsWith("api") should be(true)
  }

  "Korolev Router" should "correct do startWith with not Root path part" in {
    val path: Path = Segment("api") / "v2"

    path.startsWith("api") should be(true)
  }

  "Korolev Router" should "correct do startWith with two not Root path" in {
    val api: Path = Segment("api")
    val v2: Path = Segment("v2")
    val result = api / v2

    result.startsWith("api") should be(true)
  }

  "Korolev Router" should "correct do startWith with mixed path" in {
    val api: Path = Segment("api")
    val v2: Path = Segment("v2")
    val result = api / "en" / v2

    result.startsWith("api") should be(true)
  }
}
