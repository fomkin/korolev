package korolev.web

import korolev.web.Path._
import korolev.web.Uri._
import org.scalatest.{FlatSpec, Matchers}

class UriMatchingSpec extends FlatSpec with Matchers {
  object FirstQueryParam extends QueryParam("first")
  object FirstOptionQueryParam extends OptionQueryParam("first")
  object SecondQueryParam extends QueryParam("second")

  "Uri matching" should "correct match Path and ignore any parameter" in {
    val uri: Uri = Uri(Root / "test").withParam("test", "test")

    val pf: PartialFunction[Uri, Boolean] = {
      case Root / "test" :? _ =>
        true
    }

    pf(uri) shouldBe true
  }

  "Uri matching" should "correct extract parameters as a Map[String, String]" in {
    val uri = Uri(Root / "test").withParam("test", "test")
    val pf: PartialFunction[Uri, Boolean] = {
      case Root / "test" :? params =>
        params == Map("test" -> "test")
    }

    pf(uri) shouldBe true
  }

  "Uri matching" should "correct match parameter by name" in {
    val uri = Uri(Root / "test").withParam("first", "test")
    val pf: PartialFunction[Uri, String] = {
      case Root / "test" :? FirstQueryParam(value) =>
        value
    }

    pf(uri) shouldBe "test"
  }

  "Uri matching" should "correct match two parameter by name" in {
    val uri = Uri(Root / "test").withParam("first", "value1").withParam("second", "value2")
    val pf: PartialFunction[Uri, (String, String)] = {
      case Root / "test" :? FirstQueryParam(test) +& SecondQueryParam(untest) =>
        (test, untest)
    }

    pf(uri) shouldBe ("value1", "value2")
  }

  "Uri matching" should "fail if mandatory parameter not found" in {
    val uri = Uri(Root / "test").withParam("second", "test")
    val pf: PartialFunction[Uri, String] = {
      case Root / "test" :? FirstQueryParam(value) =>
        value
    }

    pf.isDefinedAt(uri) shouldBe false
  }

  "Uri matching" should "not fail if optional not found" in {
    val uri = Uri(Root / "test").withParam("second", "test")
    val pf: PartialFunction[Uri, Boolean] = {
      case Root / "test" :? FirstOptionQueryParam(_) =>
        false
    }

    pf.isDefinedAt(uri) shouldBe true
  }

  "Uri matching" should "correct match optional parameter" in {
    val uri = Uri(Root / "test").withParam("first", "test")
    val pf: PartialFunction[Uri, Boolean] = {
      case Root / "test" :? FirstOptionQueryParam(value) =>
        value.isDefined
    }

    pf.isDefinedAt(uri) shouldBe true
  }

  "Uri matching" should "correct match mixed parameter requirement" in {
    val uri = Uri(Root / "test").withParam("first", "value1").withParam("second", "value2")
    val pf: PartialFunction[Uri, (Option[String], String)] = {
      case Root / "test" :? FirstOptionQueryParam(test) +& SecondQueryParam(untest) =>
        (test, untest)
    }

    pf(uri) shouldBe (Some("value1"), "value2")
  }

  "Uri matching" should "correct match mixed parameter requirement with one undefined" in {
    val uri = Uri(Root / "test").withParam("second", "value1")
    val pf: PartialFunction[Uri, (Option[String], String)] = {
      case Root / "test" :? FirstOptionQueryParam(first) +& SecondQueryParam(second) =>
        (first, second)
    }

    pf(uri) shouldBe (None, "value1")
  }
}
