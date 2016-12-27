package korolev

import scala.language.higherKinds

/**
  * URL routing definition
  *
  * @tparam F A async control
  * @tparam S Type of State
  * @tparam Ctx Any additional data need
  *             to construct a state from path
  *
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
case class Router[F[_]: Async, S, Ctx]
(
    /**
      * From current state to path
      */
    fromState: PartialFunction[S, Router.Path]
      = PartialFunction.empty,
    /**
      * From path to new state using context.
      */
    toState: PartialFunction[(Ctx, Router.Path), F[S]]
      = PartialFunction.empty
)

object Router {

  type Path = String

  def empty[F[_]: Async, S, Ctx]: Router[F, S, Ctx] = Router()
}
