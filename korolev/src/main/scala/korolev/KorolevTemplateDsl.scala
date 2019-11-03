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
import levsha.Document
import Context.Binding
import levsha.dsl.SymbolDsl

/**
  * Levsha DSL with enrichments.
  */
final class KorolevTemplateDsl[F[_]: Async, S, M] extends SymbolDsl[Binding[F, S, M]] {

  implicit final class KorolevSymbolOps(s: Symbol) {

    /** define style attribute (for pixels) */
    def @=[T: Numeric](value: T): Document.Attr[Binding[F, S, M]] =
      @=(s"${value}px")

    /** define style attribute */
    def @=(value: String): Document.Attr[Binding[F, S, M]] = Document.Attr { rc =>
      rc.setStyle(s.name, value)
    }

    /** define property */
    def :=(value: String): Document.Attr[Binding[F, S, M]] = Document.Attr { rc =>
      rc.setAttr(levsha.XmlNs.html, HtmlUtil.camelCaseToSnakeCase(s.name, '^', 0), value)
    }
  }

  /**
    * Make 'a tag non-clickable
    */
  val disableHref: Document.Attr[Binding[F, S, M]] =
    'onclick /= "return false"
}
