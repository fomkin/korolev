import korolev.VDom.{Change, Node}
import korolev.{Shtml, VDom}
import org.scalatest.{FlatSpec, Matchers}

import scala.language.implicitConversions

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
class VDomSpec extends FlatSpec with Matchers {
  "changes" should "give changes" in {
    val (a, b, expectedDiff) = VDomSpec.example1
    VDom.changes(a, b) should be(expectedDiff)
  }
}

object VDomSpec extends Shtml {

  import VDom.Change._

  def example1: (Node, Node, List[Change]) = {

    val a = 'div (
      'class /= "container",
      'ul (
        'li ('text /= "Ivan"),
        'li ('text /= "Maria"),
        'li ('text /= "John")
      ),
      'div ('class /= "footer")
    )

    val b = 'div (
      'class /= "container",
      'ul (
        'li (),
        'li ('text /= "Olga"),
        'span ("Cow", 'p() ),
        'li ('text /= "Elena")
      )
    )

    implicit def toId(s: String): VDom.Id = VDom.Id(s)

    val diff = List(
      Remove("0", "0_1"),
      Create("0_0", "0_0_2", "span"),
      Create("0_0", "0_0_3", "li"),
      CreateText("0_0_2", "0_0_2_0", "Cow"),
      Create("0_0_2", "0_0_2_1", "p"),
      RemoveAttr("0_0_0", "text", isProperty = false),
      SetAttr("0_0_1", "text", "Olga", isProperty = false),
      SetAttr("0_0_3", "text", "Elena", isProperty = false)
    )
    (a, b, diff)
  }
}
