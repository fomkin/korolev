package korolev.web

import korolev.web.PathAndQuery._
import scala.language.implicitConversions
import org.scalatest.{FlatSpec, Matchers}

class PathAndQuerySpec extends FlatSpec with Matchers {
  ".fromString" should "parse path with Root" in {
    val path = PathAndQuery.fromString("/page/1")
    path shouldBe Root / "page" / "1"
  }

  ".fromString" should "parse path with empty parameters" in {
    val path = PathAndQuery.fromString("/page/1?")
    path shouldBe Root / "page" / "1"
  }

  ".fromString" should "parse path with parameters" in {
    val path = PathAndQuery.fromString("/page/1?k1=v1")
    path shouldBe Root / "page" / "1" :? "k1" -> "v1"
  }

  ".fromString" should "parse path with many parameters" in {
    val path = PathAndQuery.fromString("/page/1?k1=v1&k2=v2&k3=v3&k4=v4")
    path shouldBe Root / "page" / "1" :? "k1" -> "v1" :& "k2" -> "v2" :& "k3" -> "v3" :& "k4" -> "v4"
  }

  ".mkString" should "make from Root" in {
    val path = Root
    path.mkString shouldBe "/"
  }

  ".mkString" should "make from path with Root" in {
    val path = Root / "page"
    path.mkString shouldBe "/page"
  }

  ".mkString" should "make from path with complex path" in {
    val path: Path = Root / "api" / "v2" / "users"
    path.mkString shouldBe "/api/v2/users"
  }

  ".mkString" should "make from path with parameters" in {
    val path = Root / "page" / "1" :? "k1" -> "v1"
    path.mkString shouldBe "/page/1?k1=v1"
  }

  ".mkString" should "make from path with many parameters" in {
    val path = Root / "page" / "1" :? "k1" -> "v1" :& "k2" -> "v2" :& "k3" -> "v3" :& "k4" -> "v4" :& "k5" -> "v5"
    path.mkString shouldBe "/page/1?k1=v1&k2=v2&k3=v3&k4=v4&k5=v5"
  }

  ".fromString" should "be equal to .mkString" in {
    val result = PathAndQuery.fromString("/page/1?k1=v1&k2=v2&k3=v3&k4=v4&k5=v5")

    result.mkString shouldBe "/page/1?k1=v1&k2=v2&k3=v3&k4=v4&k5=v5"
  }

  ".endsWith" should "correct work without parameters" in {
    val path = Root / "page"
    path.endsWith("page") shouldBe true
  }

  ".endsWith" should "correct not work without parameters" in {
    val path = Root / "page"
    path.endsWith("size") shouldBe false
  }

  ".endsWith" should "correct work with parameters" in {
    val path = Root / "page" / "1" :? "k1" -> "v1"
    path.endsWith("1") shouldBe true
  }

  ".endsWith" should "correct not work with parameters" in {
    val path = Root / "page" :? "k1" -> "v1"
    path.endsWith("size") shouldBe false
  }

  ".startsWith" should "correct work without parameters" in {
    val path = Root / "page" / "1"
    path.startsWith("page") shouldBe true
  }

  ".startsWith" should "correct not work without parameters" in {
    val path = Root / "page" / "1"
    path.startsWith("size") shouldBe false
  }

  ".startsWith" should "correct work with parameters" in {
    val path = Root / "page" / "1" :? "k1" -> "v1"
    path.startsWith("page") shouldBe true
  }

  ".startsWith" should "correct not work with parameters" in {
    val path = Root / "page" / "1" :? "k1" -> "v1"
    path.startsWith("size") shouldBe false
  }

  ".++" should "correct concatenate complex path" in {
    val head = Root / "api" / "v1" / "system"
    val tail = Root / "admin" / "parameters" / "edit"

    head ++ tail shouldBe Root / "api" / "v1" / "system" / "admin" / "parameters" / "edit"
  }

  ".reverse" should "correct reverse Root path" in {
    val path = Root / "api" / "v1" / "system"

    path.reverse shouldBe Root / "system" / "v1" / "api"
  }

  "path matching" should "correct extract parameters as a Map[String, String]" in {
    val path = Root / "test" :? "k1" -> "v1"

    val pf: PartialFunction[PathAndQuery, Boolean] = {
      case Root / "test" :?* params =>
        params == Map("k1" -> "v1")
    }

    pf(path) shouldBe true
  }

  "path matching" should "correct exact match parameter" in {
    val path = Root / "test" :? "k1" -> "v1" :& "k2" -> "v2" :& "k3" -> "v3"

    val pf: PartialFunction[PathAndQuery, (String, String, String)] = {
      case Root / "test" :?? (("k1", v1), ("k2", v2), ("k3", v3)) =>
        (v1, v2, v3)
    }

    pf(path) shouldBe ("v1", "v2", "v3")
  }

  "path matching" should "correct exact match ignore parameters" in {
    val path = Root / "test" :? "k1" -> "v1"

    val pf: PartialFunction[PathAndQuery, Boolean] = {
      case Root / "test" =>
        true
      case _ =>
        false
    }

    pf(path) shouldBe true
  }

  "path matching" should "correct match parameter by name" in {
    object K1 extends QP("k1")

    val path = Root / "test" :? "k1" -> "v1"

    val pf: PartialFunction[PathAndQuery, String] = {
      case Root / "test" :?* K1(value) =>
        value
    }

    pf(path) shouldBe "v1"
  }

  "path matching" should "correct match two parameter by name" in {
    case object K1 extends QP("k1")
    case object K2 extends QP("k2")

    val path: PathAndQuery = Root :? "k1" -> "v1" :& "k2" -> "v2"
    val result = path match {
      case Root :?* K1(_) *& K2(_) =>
        true
      case _ =>
        false
    }
    result shouldBe true
  }

  "path matching" should "fail if mandatory parameter not found" in {
    object K1 extends QP("k1")
    val path = Root / "test" :? "k2" -> "v2"
    val pf: PartialFunction[PathAndQuery, String] = {
      case Root / "test" :?* K1(value) =>
        value
    }

    pf.isDefinedAt(path) shouldBe false
  }

  "path matching" should "not fail if optional not found" in {
    object K1 extends OQP("k1")
    val path = Root / "test" :? "k2" -> "v2"
    val pf: PartialFunction[PathAndQuery, Boolean] = {
      case Root / "test" :?* K1(_) =>
        true
    }

    pf(path) shouldBe true
  }

  "path matching" should "correct match optional parameter" in {
    object K1 extends OQP("k1")
    val path = Root / "test" :? "k2" -> "v2"
    val pf: PartialFunction[PathAndQuery, Option[String]] = {
      case Root / "test" :?* K1(value) =>
        value
    }

    pf(path) shouldBe None
  }

  "path matching" should "correct match mixed parameter requirement" in {
    object K1 extends OQP("k1")
    object K2 extends QP("k2")
    object K3 extends QP("k3")

    val path = Root / "test" :? "k1" -> "v1" :& "k2" -> "v2" :& "k3" -> "v3"

    val pf: PartialFunction[PathAndQuery, (Option[String], String, String)] = {
      case Root / "test" :?* K1(v1) *& K2(v2) *& K3(v3) =>
        (v1, v2, v3)
    }

    pf(path) shouldBe (Some("v1"), "v2", "v3")
  }

  "path matching" should "correct match mixed parameter requirement with one undefined" in {
    object K1 extends OQP("k1")
    object K2 extends QP("k2")
    val path = Root / "test" :? "k2" -> "v2"

    val pf: PartialFunction[PathAndQuery, (Option[String], String)] = {
      case Root / "test" :?* K1(v1) *& K2(v2) =>
        (v1, v2)
    }

    pf(path) shouldBe (None, "v2")
  }
}
