package korolev.web

import scala.annotation.tailrec

sealed trait Path {

  import Path._

  def mkString: String = {
    pathToList(Nil, this).mkString("/")
  }

  def endsWith(s: String): Boolean = this match {
    case _ / v if v.endsWith(s) => true
    case _                      => false
  }

  def startsWith(s: String): Boolean = {
    @tailrec def aux(last: String, path: Path): Boolean = path match {
      case Root                => last == s
      case RelativeRoot / next => aux(next, Root)
      case prev / x            => aux(x, prev)
//      case ++(head, _)         => aux(last, head)
    }

    aux("", this)
  }

  def /(s: String): Path = Path./(this, s)

  def ++(tail: Path): Path = {
    val headList: List[String] = pathToList(Nil, tail)
    val tailList: List[String] = pathToList(headList, this)
    val first: Path = tailList.headOption
      .map {
        case "" => Root
        case _  => RelativeRoot
      }
      .getOrElse(RelativeRoot)

    tailList
      .filter(_.nonEmpty)
      .foldLeft(first)((xs, x) => xs / x)
  }

  @tailrec
  private[this] final def pathToList(acc: List[String], path: Path): List[String] = path match {
    case Root         => "" :: acc
    case RelativeRoot => acc
    case prev / s     => pathToList(s :: acc, prev)
  }
}

object Path {
  case class /(prev: Path, value: String) extends Path

  case object Root extends Path
  case object RelativeRoot extends Path

  def fromString(raw: String): Path = {
    val first: Path = if (raw.startsWith("/")) Root else RelativeRoot

    raw
      .split("/")
      .toList
      .filter(_.nonEmpty)
      .foldLeft(first)((xs, x) => /(xs, x))
  }
}
