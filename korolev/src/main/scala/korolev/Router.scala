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
  * @tparam Ctx Any additional data need
  *             to construct a state from path
  */
case class Router[F[+_]: Async, S, Ctx](
    fromState: PartialFunction[S, Router.Path] = PartialFunction.empty,
    toState: PartialFunction[(Ctx, Router.Path), F[S]] = PartialFunction.empty
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
  }
  case class /(prev: Path, value: String) extends Path
  case object Root extends Path {
    def /(s: String): Path = Router./(this, s)
  }

  object Path {
    val fromString: String => Path = _.split("/")
      .toList
      .filter(_.nonEmpty)
      .foldLeft(Root: Path)((xs, x) => /(xs, x))
  }

  def empty[F[+_]: Async, S, Ctx]: Router[F, S, Ctx] = Router()
}
