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

package korolev

import korolev.effect.Effect
import korolev.effect.syntax._

import korolev.util.Lens
import korolev.web.PathAndQuery

/**
  * URL routing definition
  *
  * @param fromState From current state to Uri
  * @param toState From Uri to state
  *
  * @tparam F A async control
  * @tparam S Type of State
  */
final case class Router[F[_], S](
    fromState: PartialFunction[S, PathAndQuery],
    toState: PartialFunction[PathAndQuery, S => F[S]]
) {

  /**
    * Compose two routers to one.
    * {{{
    * val articlesRouter = Router(...)
    * val authorsRouter = Router(...)
    * val router = articlesRouter ++ authorsRouter
    * }}}
    */
  def ++(that: Router[F, S]): Router[F, S] =
    Router(fromState.orElse(that.fromState), toState.orElse(that.toState))
}

object Router {

  def empty[F[_], S]: Router[F, S] = Router(PartialFunction.empty, PartialFunction.empty)

  def apply[F[_]: Effect, S, S2](lens: Lens[S, S2])(
      fromState: PartialFunction[S2, PathAndQuery] = PartialFunction.empty,
      toState: PartialFunction[PathAndQuery, S2 => F[S2]] = PartialFunction.empty): Router[F, S] =
    Router(
      fromState = lens.read.andThen(fromState),
      toState = toState.andThen { f => s =>
        lens
          .get(s)
          .fold(Effect[F].pure(s)) { s2 =>
            f(s2).map(newS2 => lens.update(s, newS2))
          }
      }
    )
}
