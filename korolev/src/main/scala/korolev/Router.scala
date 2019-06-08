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

import scala.annotation.tailrec

/**
  * URL routing definition
  *
  * @param fromState From current state to path
  * @param toState From path to state
  *
  * @tparam F A async control
  * @tparam S Type of State
  */
final case class Router[F[_]: Async, S](
    fromState: PartialFunction[S, Router.Path] = PartialFunction.empty,
    toState: PartialFunction[(S, Router.Path), F[S]] = PartialFunction.empty
)

object Router {

  sealed trait Path {
    override def toString: String = {
      @tailrec def aux(acc: List[String], path: Path): List[String] = path match {
        case Root => acc
        case prev / s => aux(s :: acc, prev)
      }
      "/" + aux(Nil, this).mkString("/")
    }
    def /(s: String): Path = Router./(this, s)
  }
  case class /(prev: Path, value: String) extends Path
  case object Root extends Path

  object Path {
    val fromString: String => Path = _.split("/")
      .toList
      .filter(_.nonEmpty)
      .foldLeft(Root: Path)((xs, x) => /(xs, x))
  }

  def empty[F[_]: Async, S]: Router[F, S] = Router()
}
