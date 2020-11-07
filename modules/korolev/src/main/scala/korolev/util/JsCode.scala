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
import korolev.util.JsCode.{Element, Part}

import scala.annotation.tailrec
import scala.concurrent.Future

sealed trait JsCode {
  def ::(s: String): Part = Part(s, this)
  def ::[F[_]](s: ElementId[F]): Element[F] = Element(s, this)

  def mkString[F[_]](elementToId: ElementId[F] => levsha.Id): String = {
    @tailrec
    def aux(acc: String, jsCode: JsCode): String = jsCode match {
      case JsCode.End => acc
      case JsCode.Part(x, xs) => aux(acc + x, xs)
      case JsCode.Element(x, xs) =>
        val id = elementToId(x.asInstanceOf[ElementId[F]])
        aux(acc + s"Korolev.element('$id')", xs)
    }
    aux("", this)
  }
}

object JsCode {

  case class Part(value: String, tail: JsCode) extends JsCode
  case class Element[F[_]](elementId: ElementId[F], tail: JsCode) extends JsCode
  case object End extends JsCode

  def apply(s: String): JsCode = s :: End

  def apply(parts: List[String], inclusions: List[Any]): JsCode = {
    @tailrec
    def combine(acc: JsCode, ps: List[String], is: List[Any]): JsCode = ps match {
      case Nil => acc
      case px :: pxs =>
        is match {
          case (ix: ElementId[_]) :: ixs => combine(ix :: px :: acc, pxs, ixs)
          case (ix: String) :: ixs => combine(ix :: px :: acc, pxs, ixs)
          case Nil => combine(px :: acc, pxs, Nil)
        }
    }
    @tailrec
    def reverse(acc: JsCode, jsCode: JsCode): JsCode = jsCode match {
      case Part(x, xs) => reverse(x :: acc, xs)
      case Element(x, xs) => reverse(x.asInstanceOf[ElementId[Future]] :: acc, xs)
      case End => acc
    }
    reverse(End, combine(End, parts, inclusions))
  }

}