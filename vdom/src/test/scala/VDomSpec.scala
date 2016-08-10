import korolev.{Shtml, VDom}
import org.scalatest.{FlatSpec, Matchers}

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

  def example1 = {

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

    val diff = List(
      RemoveAttr("el_0_0_0", 'text),
      SetAttr("el_0_0_1", 'text, "Olga"),
      CreateText("el_0_0_2", "el_0_0_2_0", "Cow"),
      Create("el_0_0_2", "el_0_0_2_1", 'p),
      SetAttr("el_0_0_3", 'text, "Elena"),
      Create("el_0_0", "el_0_0_2", 'span),
      Create("el_0_0", "el_0_0_3", 'li),
      Remove("el_0_1")
    )
    (a, b, diff)
  }
}
