package korolev

import scala.annotation.tailrec
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
case class Router[F[+_]: Async, S, Ctx]
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
