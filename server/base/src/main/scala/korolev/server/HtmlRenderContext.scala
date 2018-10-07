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

package korolev.server

import korolev.{Context, Async}
import Context._
import korolev.utils.HtmlUtil
import levsha.{RenderContext, XmlNs}
import levsha.impl.{AbstractTextRenderContext, TextPrettyPrintingConfig}

import scala.collection.mutable

final class HtmlRenderContext[F[+_]: Async, S, M] extends AbstractTextRenderContext[Effect[F, S, M]] {

  import HtmlRenderContext._

  private val styleSb = new mutable.StringBuilder()
  private var lastOp = OpClose

  private def appendStyle() = {
    if (lastOp != OpClose && lastOp != OpText && styleSb.nonEmpty) {
      builder.append(""" style="""")
      builder.append(styleSb)
      builder.append('"')
      styleSb.clear()
    }
  }

  val prettyPrinting = TextPrettyPrintingConfig.noPrettyPrinting

  override def addMisc(misc: Effect[F, S, M]): Unit = misc match {
    case ComponentEntry(component, parameters, _) =>
      val rc = this.asInstanceOf[RenderContext[Context.Effect[F, Any, Any]]]
      // Static pages always made from scratch
      component.render(parameters, component.initialState).apply(rc)
    case _ => ()
  }

  override def setAttr(xmlNs: XmlNs, name: String, value: String): Unit = {
    lastOp = OpAttr
    if (name.charAt(0) == '*') {
      HtmlUtil.camelCaseToSnakeCase(styleSb, name, startIndex = 1)
      styleSb.append(':')
      styleSb.append(value)
      styleSb.append(';')
      ()
    } else {
      super.setAttr(xmlNs, name, value)
    }
  }

  override def addTextNode(text: String): Unit = {
    appendStyle()
    lastOp = OpText
    super.addTextNode(text)
  }

  override def openNode(xmlNs: XmlNs, name: String): Unit = {
    appendStyle()
    lastOp = OpOpen
    super.openNode(xmlNs, name)
  }

  override def closeNode(name: String): Unit = {
    appendStyle()
    lastOp = OpClose
    super.closeNode(name)
  }
}

object HtmlRenderContext {

  final val OpOpen = 1
  final val OpClose = 2
  final val OpAttr = 3
  final val OpText = 4
  final val OpLastAttr = 5
  final val OpEnd = 6
}
