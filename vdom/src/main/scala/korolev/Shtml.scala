package korolev

import korolev.VDom._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.language.implicitConversions

trait Shtml {

  import Shtml._

  implicit def toTextNode(text: String): Text = Text(text)

  implicit def toOptionNode(opt: Option[VDom]): VDom = opt match {
    case Some(nl) => nl
    case None => <>
  }

  implicit def toNodeLikes(xs: Iterable[NodeLike]): NodeLikes = NodeLikes(xs.toList)

  implicit def ShtmlSymbolOps(name: Symbol): ShtmlSymbolOps =
    new ShtmlSymbolOps(name)

  val <> = VDom.Empty
}

object Shtml {

  val nameCache = mutable.Map.empty[Symbol, String]

  val twoWayBindingDefaultEvents = Seq("input", "change")

  def htmlName(x: Symbol): String = {
    nameCache.getOrElseUpdate(
        x, x.name.replaceAll("([A-Z]+)", "-$1").toLowerCase)
  }

  final class ShtmlSymbolOps(val self: Symbol) extends AnyVal {

    def apply(vdom: VDom*): Node = {
      @tailrec
      def loop(children: List[NodeLike],
               attrs: List[Attr],
               misc: List[Misc],
               tl: List[VDom]): Node = tl match {
        case Nil => Node(htmlName(self), children.reverse, attrs.reverse, misc.reverse)
        case (x: NodeLike) :: xs => loop(x :: children, attrs, misc, xs)
        case (x: Attr) :: xs => loop(children, x :: attrs, misc, xs)
        case (x: Misc) :: xs => loop(children, attrs, x :: misc, xs)
        case NodeLikes(nodes) :: xs =>
          loop(children ::: nodes.reverse, attrs, misc, xs)
        case _ :: xs =>
          loop(children, attrs, misc, xs)
      }
      loop(Nil, Nil, Nil, vdom.toList)
    }

    def :=(value: Any): Attr =
      Attr(self.name, value, isProperty = true)

    def /=(value: String): Attr =
      Attr(htmlName(self), value, isProperty = false)
  }
//
//  sealed trait InputType
//
//  object InputType {
//    case object Text extends InputType
//  }
}
