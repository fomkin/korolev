package korolev.web

import org.scalatest.{FlatSpec, Matchers}

class PathMatchingSpec extends FlatSpec with Matchers {
  import korolev.web.Path._

  "Path" should "correct add leading slash for Root" in {
    val result: Path = Root / "api"

    result should be(Root / "api")
    result.mkString should be("/api")
  }

  "Path" should "not add leading slash for RelativeRoot" in {
    val result: Path = RelativeRoot / "api"

    result should be(RelativeRoot / "api")
    result.mkString should be("api")
  }

  "Path" should "correct concatenate Root path with RelativeRoot path" in {
    val head: Path = Root / "api" / "v2"
    val tail: Path = RelativeRoot / "users"

    val result: Path = head ++ tail

    result should be(Root / "api" / "v2" / "users")
    result.mkString should be("/api/v2/users")
  }

  "Path" should "correct concatenate two RelativeRoot path" in {
    val head: Path = RelativeRoot / "api" / "v2"
    val tail: Path = RelativeRoot / "users"

    val result: Path = head ++ tail

    result should be(RelativeRoot / "api" / "v2" / "users")
    result.mkString should be("api/v2/users")
  }

  "Path" should "correct match two concatenated path" in {
    val head: Path = RelativeRoot / "api" / "v2"
    val tail: Path = RelativeRoot / "users"

    val path: Path = head ++ tail

    val result = path match {
      case RelativeRoot / "api" / "v2" / "users" =>
        true
      case _ =>
        false
    }

    result should be(true)
  }

  "Path" should "correct match two concatenated path with simple Path" in {
    val head: Path = Root / "api" / "v2"
    val tail: Path = RelativeRoot / "users"

    val path: Path = Root ++ RelativeRoot / "api" / "v2" / "users"

    val result = path match {
      case Root / "api" / "v2" / "users" =>
        true
      case other =>
        false
    }

    result should be(true)
  }

  "Path" should "correct concatenate three path" in {
    val first: Path = Root / "api"
    val second: Path = RelativeRoot / "v2"
    val third: Path = RelativeRoot / "users"

    val result: Path = first ++ second ++ third

    result should be(Root / "api" / "v2" / "users")
    result.mkString should be("/api/v2/users")
  }

  "Path" should "correct concatenate two Root path" in {
    val head: Path = Root / "api" / "v2"
    val tail: Path = Root / "users"

    val result: Path = head ++ tail

    result should be(Root / "api" / "v2" / "users")
    result.mkString should be("/api/v2/users")
  }

  "Path" should "correct do endsWith with RelativeRoot" in {
    val api: Path = RelativeRoot / "api" / "v2"

    api.endsWith("v2") should be(true)
  }

  "Path" should "correct do endsWith after concatenation" in {
    val head: Path = Root / "api" / "v2"
    val tail: Path = Root / "users"

    val result: Path = head ++ tail

    result.endsWith("users") should be(true)
  }

  "Path" should "correct do startWith with RelativeRoot" in {
    val result: Path = RelativeRoot / "api"

    result.startsWith("api") should be(true)
  }

  "Path" should "correct do startWith after concatenation" in {
    val head: Path = RelativeRoot / "api"
    val tail: Path = RelativeRoot / "v2"
    val result = head / "en" ++ tail

    result.startsWith("api") should be(true)
  }

  "Path" should "correct do startWith after concatenation with Root" in {
    val head: Path = Root / "api"
    val tail: Path = RelativeRoot / "v2"
    val result = head / "en" ++ tail

    result.startsWith("api") should be(true)
  }
}
