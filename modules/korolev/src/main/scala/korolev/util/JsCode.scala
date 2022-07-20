/*
 * Copyright 2017-2020 Aleksey Fomkin
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

package korolev.util

import korolev.Context.ElementId
import korolev.context.Binding
import korolev.util.JsCode.{Element, Part}

import scala.annotation.tailrec

sealed trait JsCode {
  def ::(s: String): Part = Part(s, this)
  def ::(s: ElementId): Element = Element(s, this)

  def mkString(elementToId: Binding.Element => levsha.Id): String = {
    @tailrec
    def aux(acc: String, jsCode: JsCode): String = jsCode match {
      case JsCode.End => acc
      case JsCode.Part(x, xs) => aux(acc + x, xs)
      case JsCode.Element(x, xs) =>
        val id = elementToId(x.asInstanceOf[Binding.Element])
        aux(acc + s"Korolev.element('$id')", xs)
    }
    aux("", this)
  }
}

object JsCode {

  case class Part(value: String, tail: JsCode) extends JsCode
  case class Element(elementId: ElementId, tail: JsCode) extends JsCode
  case object End extends JsCode

  def apply(s: String): JsCode = s :: End

  def apply(parts: List[String], inclusions: List[Any]): JsCode = {
    @tailrec
    def combine(acc: JsCode, ps: List[String], is: List[Any]): JsCode = ps match {
      case Nil => acc
      case px :: pxs =>
        is match {
          case (ix: ElementId) :: ixs => combine(ix :: px :: acc, pxs, ixs)
          case (ix: String) :: ixs => combine(ix :: px :: acc, pxs, ixs)
          case ix :: ixs => combine(ix.toString :: px :: acc, pxs, ixs)
          case Nil => combine(px :: acc, pxs, Nil)
        }
    }
    @tailrec
    def reverse(acc: JsCode, jsCode: JsCode): JsCode = jsCode match {
      case Part(x, xs) => reverse(x :: acc, xs)
      case Element(x, xs) => reverse(x.asInstanceOf[ElementId] :: acc, xs)
      case End => acc
    }
    reverse(End, combine(End, parts, inclusions))
  }

}