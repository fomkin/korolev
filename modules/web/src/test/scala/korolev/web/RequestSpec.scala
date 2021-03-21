package korolev.web

import korolev.web.Request.Method
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RequestSpec extends AnyFlatSpec with Matchers {

  private val defaultRequest = Request(Method.Get, PathAndQuery.Root, Nil, None, ())

  "parsedCookies" should "parse cookies in correct format" in {
    val request = defaultRequest.copy(renderedCookie = "a=1;b=2")
    request.cookie("a") shouldEqual Some("1")
    request.cookie("b") shouldEqual Some("2")
  }

  it should "parse cookies without values" in {
    val request = defaultRequest.copy(renderedCookie = "a;b=2")
    request.cookie("a") shouldEqual Some("")
    request.cookie("b") shouldEqual Some("2")
  }

  it should "parse cookies in invalid format" in {
    val request = defaultRequest.copy(renderedCookie = "a=1=2;b=2")
    request.cookie("a") shouldEqual None
    request.cookie("b") shouldEqual Some("2")
  }
}
