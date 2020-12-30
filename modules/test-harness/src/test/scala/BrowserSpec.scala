import org.scalatest.{FlatSpec, Matchers}
import korolev.testkit._
import levsha.{Id, XmlNs}

class BrowserSpec extends FlatSpec with Matchers  {

  "Browser.render" should "map levsha.Node to PseudoDom.Element" in {
    import levsha.dsl._
    import html._

    val node = div()
    val rr = Browser.render(node)

    rr.pseudoDom shouldEqual PseudoDom.Element(Id("1"), XmlNs.html, "div", Map.empty, Map.empty, List.empty)
  }

  it should "map nested levsha.Node to correspondent pseudo DOM elements" in {
    import levsha.dsl._
    import html._
    import PseudoDom._

    val node = body(ul(li("1"), li("2"), li("3")))
    val rr = Browser.render(node)

    rr.pseudoDom shouldEqual Element(Id("1"), XmlNs.html, "body", Map.empty, Map.empty, List(
      Element(Id("1_1"), XmlNs.html, "ul", Map.empty, Map.empty, List(
        Element(Id("1_1_1"), XmlNs.html, "li", Map.empty, Map.empty, List(Text(Id("1_1_1_1"), "1"))),
        Element(Id("1_1_2"), XmlNs.html, "li", Map.empty, Map.empty, List(Text(Id("1_1_2_1"), "2"))),
        Element(Id("1_1_3"), XmlNs.html, "li", Map.empty, Map.empty, List(Text(Id("1_1_3_1"), "3"))),
      ))
    ))
  }

  it should "map attributes well" in {
    import levsha.dsl._
    import html._

    val node = div(clazz := "foo bar", id := "baz")
    val rr = Browser.render(node)

    rr.pseudoDom shouldEqual PseudoDom.Element(Id("1"), XmlNs.html, "div", Map("class" -> "foo bar", "id" -> "baz"), Map.empty, List.empty)
  }

  it should "map styles well" in {
    import levsha.dsl._
    import html._

    val node = div(backgroundColor @= "red", border @= "1px")
    val rr = Browser.render(node)

    rr.pseudoDom shouldEqual PseudoDom.Element(Id("1"), XmlNs.html, "div", Map.empty, Map("background-color" -> "red", "border" -> "1px"), List.empty)
  }

}
