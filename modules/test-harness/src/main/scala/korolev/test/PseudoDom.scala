package korolev.test

import scala.annotation.tailrec

sealed trait PseudoDom {

  import PseudoDom._

  def find(f: PseudoDom => Boolean): List[PseudoDom] = {
    @tailrec def aux(acc: List[PseudoDom], rest: List[PseudoDom]): List[PseudoDom] = rest match {
      case (e: Text) :: xs if f(e) => aux(e :: acc, xs)
      case (_: Text) :: xs => aux(acc, xs)
      case (e: Element) :: xs if f(e) => aux(e :: acc, xs ::: e.children)
      case (e: Element) :: xs => aux(acc, xs ::: e.children)
    }
    aux(Nil, List(this))
  }

  def byAttribute(name: String, f: String => Boolean): List[PseudoDom] = find {
    case e: Element => e.attributes.get(name).exists(f)
    case _: Text => false
  }

  def byClass(clazz: String): List[PseudoDom] =
    byAttribute("class", _.indexOf(clazz) > -1)

  def byName(name: String): List[PseudoDom] = find {
    case e: Element => e.name == name
    case _: Text => false
  }

  def text: String
}

object PseudoDom {

  case class Element(name: String,
                     attributes: Map[String, String],
                     children: List[PseudoDom]) extends PseudoDom {

    lazy val text: String =
      children.reduce(_.text + _.text)
  }

  case class Text(value: String) extends PseudoDom {
    val text: String = value
  }
}

