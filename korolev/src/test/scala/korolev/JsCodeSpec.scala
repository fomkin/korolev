package korolev

import korolev.util.JsCode
import korolev.Context.ElementId
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Future

class JsCodeSpec extends FlatSpec with Matchers {

  import JsCode._

  "JsCode.apply" should "construct correct list" in {
    val el1 = new ElementId[Future](Some("el1"))
    val el2 = new ElementId[Future](Some("el2"))
    val jsCode = JsCode(List("--", "++", "//"), List(el1, el2))

    jsCode should equal(Part("--", Element(el1, Part("++", Element(el2, Part("//", End))))))
  }

  "jsCode.mkString" should "construct correct string" in {
    val el1 = new ElementId[Future](Some("el1"))
    val el2 = new ElementId[Future](Some("el2"))
    val el2id: ElementId[Future] => levsha.Id = {
      case `el1` => levsha.Id("1_1")
      case `el2` => levsha.Id("1_2")
    }
    val jsCode = "swapElements(" :: el1 :: ", " :: el2 :: ");" :: End

    jsCode.mkString(el2id) should equal("swapElements(Korolev.element('1_1'), Korolev.element('1_2'));")
  }
}
