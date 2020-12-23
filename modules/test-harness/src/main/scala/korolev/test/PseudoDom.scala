package korolev.test

import levsha.Id

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

  def byName(name: String): List[PseudoDom] =
    byAttribute("name", _ == name)

  def byTag(tagName: String): List[PseudoDom] = find {
    case e: Element => e.tagName == tagName
    case _: Text => false
  }

  def text: String
}

object PseudoDom {

  case class Element(id: Id,
                     tagName: String,
                     attributes: Map[String, String],
                     children: List[PseudoDom]) extends PseudoDom {

    lazy val text: String =
      children.foldLeft("")(_ + _.text)
  }

  case class Text(id: Id, value: String) extends PseudoDom {
    val text: String = value
  }
}

