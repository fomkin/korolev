package korolev.testkit

import korolev.Context
import korolev.Context.{Binding, ComponentEntry, ElementId}
import levsha.Document.Node
import levsha.events.EventId
import levsha.{Id, IdBuilder, RenderContext, XmlNs}

import scala.annotation.tailrec

sealed trait PseudoHtml {

  import PseudoHtml._

  def id: Id

  def find(f: PseudoHtml => Boolean): List[PseudoHtml] = {
    @tailrec def aux(acc: List[Element], rest: List[PseudoHtml]): List[Element] = rest match {
      case Nil => acc
      case (_: Text) :: xs => aux(acc, xs)
      case (e: Element) :: xs if f(e) => aux(e :: acc, xs ::: e.children)
      case (e: Element) :: xs => aux(acc, xs ::: e.children)
    }
    aux(Nil, List(this))
  }

  def findElement(f: Element => Boolean): List[Element] = {
    @tailrec def aux(acc: List[Element], rest: List[PseudoHtml]): List[Element] = rest match {
      case Nil => acc
      case (_: Text) :: xs => aux(acc, xs)
      case (e: Element) :: xs if f(e) => aux(e :: acc, xs ::: e.children)
      case (e: Element) :: xs => aux(acc, xs ::: e.children)
    }
    aux(Nil, List(this))
  }

  def byAttribute(name: String, f: String => Boolean): List[Element] =
    findElement(_.attributes.get(name).exists(f))

  def byClass(clazz: String): List[Element] =
    byAttribute("class", _.indexOf(clazz) > -1)

  def byName(name: String): List[Element] =
    byAttribute("name", _ == name)

  def byTag(tagName: String): List[PseudoHtml] =
    findElement(_.tagName == tagName)

  def mkString: String = {
    def aux(node: PseudoHtml, ident: String): String = node match {
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
        val renderedChildren = children.map(aux(_, ident + "  ")).mkString("\n")
        s"$ident<$tagName$renderedStyles$renderedAttributes> <!-- ${id.mkString} -->\n$renderedChildren\n$ident</$tagName>"
    }
    aux(this, "")
  }

  def text: String
}

object PseudoHtml {

  case class Element(id: Id,
                     ns: XmlNs,
                     tagName: String,
                     attributes: Map[String, String],
                     styles: Map[String, String],
                     children: List[PseudoHtml]) extends PseudoHtml {

    lazy val text: String =
      children.foldLeft("")(_ + _.text)
  }

  case class Text(id: Id, value: String) extends PseudoHtml {
    val text: String = value
  }

  private class PseudoDomRenderContext[F[_], S, M] extends RenderContext[Binding[F, S, M]] {

    val idBuilder = new IdBuilder(256)
    var currentChildren: List[List[PseudoHtml]] = List(Nil)
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
      val node = PseudoHtml.Element(
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
      val updatedChildren = PseudoHtml.Text(idBuilder.mkId, text) :: children
      currentChildren = updatedChildren :: xs
    }

    def addMisc(misc: Binding[F, S, M]): Unit = {
      idBuilder.decLevel()
      misc match {
        case ComponentEntry(c, p, _) =>
          val rc = this.asInstanceOf[RenderContext[Context.Binding[F, Any, Any]]]
          c.render(p, c.initialState).apply(rc)
        case elementId: ElementId =>
          elements = (idBuilder.mkId, elementId) :: elements
        case event: Context.Event[F, S, M] =>
          val eventId = EventId(idBuilder.mkId, event.`type`, event.phase)
          events = (eventId, event) :: events
      }
      idBuilder.incLevel()
    }
  }

  case class RenderingResult[F[_], S, M](pseudoDom: PseudoHtml,
                                         elements: Map[levsha.Id, ElementId],
                                         events: Map[EventId, Context.Event[F, S, M]])

  def render[F[_], S, M](node: Node[Binding[F, S, M]]): RenderingResult[F, S, M] = {

    val rc = new PseudoDomRenderContext[F, S, M]()
    node(rc)
    val (top :: _) :: _ = rc.currentChildren
    RenderingResult(top, rc.elements.toMap, rc.events.toMap)
  }

}

