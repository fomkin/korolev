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
  * @tparam F Control monad
  * @tparam S State of the component
  * @tparam E Type of events produced by component
  */
sealed trait ComponentBase[F[_], S, P, E] {

  /**
    * Default component state
    */
  def initialState: S

  /**
    * Component context.
    *
    * {{{
    *  import context._
    * }}}
    */
  val context: Context[F, S, E] = Context[F, S, E]
}

/**
  * Component definition. Every Korolev application is a component.
  * Extent it to declare component in object oriented style.
  *
  * @tparam F Control monad
  * @tparam S State of the component
  * @tparam E Type of events produced by component
  */
trait Component[F[_], S, P, E] extends ComponentBase[F, S, P, E] {

  /**
    * Component render
    */
  def render(parameters: P, state: S): Node[Binding[F, S, E]]
}

/**
  * Asynchronous Component definition.
  *
  * @tparam F Control monad
  * @tparam S State of the component
  * @tparam E Type of events produced by component
  */
trait AsyncComponent[F[_], S, P, E] extends ComponentBase[F, S, P, E] {

  /**
    * Asynchronous Component render
    */
  def render(parameters: P, state: S): Node[Binding[F, S, E]]

  /**
    * Component placeholder that rendered in synchronous way for static page requests
    */
  def placeholder(parameters: P, state: S): Node[Binding[F, S, E]]
}

object Component {
//  private[korolev] def randomId() = Random.alphanumeric.take(6).mkString
//  final val TopLevelComponentId = "top-level"

  /** (context, state) => document */
  type Render[F[_], S, P, E] = (Context[F, S, E], P, S) => Node[Binding[F, S, E]]

  /**
    * Create component in functional style
    * @param f Component renderer
    * @see [[Component]]
    */
  def apply[F[_]: Effect, S: StateSerializer: StateDeserializer, P, E](defaultState: S)(
      f: Render[F, S, P, E]): Component[F, S, P, E] = {
    new Component[F, S, P, E] {
      override val initialState: S = defaultState

      override def render(parameters: P, state: S): Node[Binding[F, S, E]] = f(context, parameters, state)
    }
  }

  /** (context, state) => F[document] */
//  type AsyncRender[F[_], S, P, E] = (P, S) => F[Node[Binding[F, S, E]]]

  /**
    * Create component in functional style
    * @param p Component placeholder renderer
    * @param r Component asynchronous renderer
    * @see [[Component]]
    */
  def apply[F[_]: Effect, S: StateSerializer: StateDeserializer, P, E](
      defaultState: S)(p: Render[F, S, P, E], r: Render[F, S, P, E]): AsyncComponent[F, S, P, E] = {
    new AsyncComponent[F, S, P, E] {
      override val initialState: S = defaultState

      /**
        * Asynchronous Component render
        */
      override def render(parameters: P, state: S): Node[Binding[F, S, E]] = r(context, parameters, state)

      /**
        * Component placeholder that rendered in synchronous way for static page requests
        */
      override def placeholder(parameters: P, state: S): Node[Binding[F, S, E]] = p(context, parameters, state)
    }
  }
}
