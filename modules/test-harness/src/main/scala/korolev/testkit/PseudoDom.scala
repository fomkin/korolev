package korolev.testkit

import korolev.Context
import korolev.Context.{Binding, ComponentEntry, ElementId}
import levsha.Document.Node
import levsha.events.EventId
import levsha.{Id, IdBuilder, RenderContext, XmlNs}

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

  private class PseudoDomRenderContext[F[_], S, M] extends RenderContext[Binding[F, S, M]] {

    val idBuilder = new IdBuilder(256)
    var currentChildren: List[List[PseudoDom]] = List(Nil)
    var currentNode = List.empty[(XmlNs, String)]
    var currentAttrs = List.empty[(XmlNs, String, String)]
    var currentStyles = List.empty[(String, String)]

    var elements = List.empty[(levsha.Id, ElementId)]
    var events = List.empty[(EventId, Context.Event[F, S, M])]

    def openNode(xmlns: XmlNs, name: String): Unit = {
      currentNode = (xmlns, name) :: currentNode
      currentChildren = Nil :: currentChildren
      currentAttrs = Nil
      currentStyles = Nil
      idBuilder.incId()
      idBuilder.incLevel()
    }

    def closeNode(name: String): Unit = {
      idBuilder.decLevel()
      val (xmlns, _) :: currentNodeTail = currentNode
      val children :: currentChildrenTail = currentChildren
      val c2 :: cct2 = currentChildrenTail
      val node = PseudoDom.Element(
        id = idBuilder.mkId,
        ns = xmlns,
        tagName = name,
        attributes = currentAttrs.map(x => (x._2, x._3)).toMap,
        styles = currentStyles.toMap,
        children = children.reverse
      )
      currentNode = currentNodeTail
      currentChildren = (node :: c2) :: cct2
    }

    def setAttr(xmlNs: XmlNs, name: String, value: String): Unit = {
      currentAttrs = (xmlNs, name, value) :: currentAttrs
    }

    def setStyle(name: String, value: String): Unit = {
      currentStyles = (name, value) :: currentStyles
    }

    def addTextNode(text: String): Unit = {
      idBuilder.incId()
      val children :: xs = currentChildren
      val updatedChildren = PseudoDom.Text(idBuilder.mkId, text) :: children
      currentChildren = updatedChildren :: xs
    }

    def addMisc(misc: Binding[F, S, M]): Unit = misc match {
      case ComponentEntry(c, p, _) =>
        val rc = this.asInstanceOf[RenderContext[Context.Binding[F, Any, Any]]]
        c.render(p, c.initialState).apply(rc)
      case elementId: ElementId =>
        elements = (idBuilder.mkId, elementId) :: elements
      case event: Context.Event[F, S, M] =>
        val eventId = EventId(idBuilder.mkId, event.`type`, event.phase)
        events = (eventId, event) :: events
    }
  }

  case class RenderingResult[F[_], S, M](pseudoDom: PseudoDom,
                                         elements: Map[levsha.Id, ElementId],
                                         events: Map[EventId, Context.Event[F, S, M]])

  def render[F[_], S, M](node: Node[Binding[F, S, M]]): RenderingResult[F, S, M] = {

    val rc = new PseudoDomRenderContext[F, S, M]()
    node(rc)
    val (top :: _) :: _ = rc.currentChildren
    RenderingResult(top, rc.elements.toMap, rc.events.toMap)
  }

}

