package korolev

import korolev.utils.HtmlUtil
import levsha.Document.Empty
import levsha.{Document, TemplateDsl}
import Context.Effect

/**
  * Levsha DSL with enrichments.
  */
final class KorolevTemplateDsl[F[+_]: Async, S, M] extends TemplateDsl[Effect[F, S, M]] {

  type Document = levsha.Document[Effect[F, S, M]]
  type Node     = levsha.Document.Node[Effect[F, S, M]]
  type Attr     = levsha.Document.Attr[Effect[F, S, M]]

  @deprecated("Use Node instead of VDom", "0.4.0")
  type VDom = Node

  @deprecated("Use void instead of <>", since = "0.4.0")
  val <> = Empty

  /**
    * Make 'a tag non-clickable
    */
  val disableHref = 'onclick /= "return false"

  implicit final class KorolevSymbolOps(s: Symbol) {
    def @=[T: Numeric](value: T): Document.Attr[Effect[F, S, M]] =
      @=(s"${value}px")

    def @=(value: String): Document.Attr[Effect[F, S, M]] = Document.Attr { rc =>
      rc.setAttr(levsha.XmlNs.html, '*' + s.name, value)
    }
    def :=(value: String): Document.Attr[Effect[F, S, M]] = Document.Attr { rc =>
      rc.setAttr(levsha.XmlNs.html, HtmlUtil.camelCaseToSnakeCase(s.name, '^', 0), value)
    }
  }
}
