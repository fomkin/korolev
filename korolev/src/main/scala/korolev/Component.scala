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

import korolev.Context._
import korolev.effect.Effect
import korolev.state.{StateDeserializer, StateSerializer}
import levsha.Document.Node

import scala.util.Random

/**
  * Component definition. Every Korolev application is a component.
  * Extent it to declare component in object oriented style.
  *
  * @param id Unique identifier of the component.
  *           Use it when you create component declaration dynamically
  *
  * @tparam F Control monad
  * @tparam S State of the component
  * @tparam E Type of events produced by component
  */
abstract class Component
  [
    F[_]: Effect,
    S: StateSerializer: StateDeserializer, P, E
  ](
    val initialState: S,
    val id: String = Component.randomId()
  ) {

  /**
    * Component context.
    *
    * {{{
    *  import context._
    *  import symbolDsl._
    * }}}
    */
  val context = Context[F, S, E]

  /**
    * Component render
    */
  def render(parameters: P, state: S): context.Node
}

object Component {

  /** (context, state) => document */
  type Render[F[_], S, P, E] = (Context[F, S, E], P, S) => Node[Binding[F, S, E]]

  /**
    * Create component in functional style
    * @param f Component renderer
    * @see [[Component]]
    */
  def apply[F[_]: Effect, S: StateSerializer: StateDeserializer, P, E]
           (initialState: S, id: String = Component.randomId())
           (f: Render[F, S, P, E]): Component[F, S, P, E] = {
    new Component[F, S, P, E](initialState, id) {
      def render(parameters: P, state: S): Node[Binding[F, S, E]] = f(context, parameters, state)
    }
  }

  final val TopLevelComponentId = "top-level"

  private[korolev] def randomId() = Random.alphanumeric.take(6).mkString
}
