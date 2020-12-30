package korolev.testkit

import levsha.{Id, XmlNs}

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

  def mkString: String = {
    def aux(node: PseudoDom, ident: String): String = node match {
      case Text(id, value) => s"$ident$value <!-- ${id.mkString} -->"
      case Element(id, _, tagName, attributes, styles, children) =>
        val renderedAttributes = attributes
          .map { case (k, v) => s"""$k="$v"""" }
          .mkString(" ")
        val renderedStyles = if (styles.nonEmpty) {
          val s = styles
            .map { case (k, v) => s"""$k: $v""" }
            .mkString("; ")
          s""" style="$s""""
        } else {
          " "
        }
        val renderedChildren = children.map(aux(_, ident + "  "))
        s"$ident<$tagName$renderedStyles$renderedAttributes> <!-- ${id.mkString} -->\n$renderedChildren\n$ident</$tagName>"
    }
    aux(this, "")
  }

  def text: String
}

object PseudoDom {

  case class Element(id: Id,
                     ns: XmlNs,
                     tagName: String,
                     attributes: Map[String, String],
                     styles: Map[String, String],
                     children: List[PseudoDom]) extends PseudoDom {

    lazy val text: String =
      children.foldLeft("")(_ + _.text)
  }

  case class Text(id: Id, value: String) extends PseudoDom {
    val text: String = value
  }
}

