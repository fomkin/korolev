/*
 * Copyright 2017-2018 Aleksey Fomkin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package korolev

import korolev.utils.HtmlUtil
import levsha.Document.Empty
import levsha.Document
import Context.Effect
import levsha.dsl.SymbolDsl

/**
  * Levsha DSL with enrichments.
  */
final class KorolevTemplateDsl[F[+_]: Async, S, M] extends SymbolDsl[Effect[F, S, M]] {

  type Document = levsha.Document[Effect[F, S, M]]
  type Node     = levsha.Document.Node[Effect[F, S, M]]
  type Attr     = levsha.Document.Attr[Effect[F, S, M]]

  implicit final class KorolevSymbolOps(s: Symbol) {

    /** define style attribute (for pixels) */
    def @=[T: Numeric](value: T): Document.Attr[Effect[F, S, M]] =
      @=(s"${value}px")

    /** define style attribute */
    def @=(value: String): Document.Attr[Effect[F, S, M]] = Document.Attr { rc =>
      rc.setAttr(levsha.XmlNs.html, '*' + s.name, value)
    }

    /** define property */
    def :=(value: String): Document.Attr[Effect[F, S, M]] = Document.Attr { rc =>
      rc.setAttr(levsha.XmlNs.html, HtmlUtil.camelCaseToSnakeCase(s.name, '^', 0), value)
    }
  }

  @deprecated("Use Node instead of VDom", "0.4.0")
  type VDom = Node

  @deprecated("Use void instead of <>", since = "0.4.0")
  val <> : Document.Empty.type = Empty

  /**
    * Make 'a tag non-clickable
    */
  val disableHref: Document.Attr[Effect[F, S, M]] =
    'onclick /= "return false"
}
