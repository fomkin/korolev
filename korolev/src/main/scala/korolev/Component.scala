package korolev

import korolev.ApplicationContext._
import korolev.execution.Scheduler
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
abstract class Component[F[+ _]: Async: Scheduler, S, P, E](val initialState: S,
                                                            val id: String = Component.randomId()) {

  /**
    * Component context.
    *
    * {{{
    *  import context._
    *  import symbolDsl._
    * }}}
    */
  val context = ApplicationContext[F, S, E]

  /**
    * Component render
    */
  def render(parameters: P, state: S): context.symbolDsl.Node
}

object Component {

  /** (context, state) => document */
  type Render[F[+ _], S, P, E] = (ApplicationContext[F, S, E], P, S) => Node[Effect[F, S, E]]

  /**
    * Create component in functional style
    * @param f Component renderer
    * @see [[Component]]
    */
  def apply[F[+ _]: Async: Scheduler, S, P, E](initialState: S, id: String = Component.randomId())(
      f: Render[F, S, P, E]): Component[F, S, P, E] = {
    new Component[F, S, P, E](initialState, id) {
      def render(parameters: P, state: S): Node[Effect[F, S, E]] = f(context, parameters, state)
    }
  }

  final val TopLevelComponentId = "top-level"

  private[korolev] def randomId() = Random.alphanumeric.take(6).mkString
}
