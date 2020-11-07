package korolev.web

import scala.annotation.tailrec

sealed trait Path {

  import Path._

  def mkString: String = {
    @tailrec def aux(acc: List[String], path: Path): List[String] = path match {
      case Root => acc
      case prev / s => aux(s :: acc, prev)
    }
    "/" + aux(Nil, this).mkString("/")
  }

  def endsWith(s: String): Boolean = this match {
    case _ / v if v.endsWith(s) => true
    case _ => false
  }

  def startsWith(s: String): Boolean = {
    @tailrec def aux(last: String, path: Path): Boolean = path match {
      case Root => last == s
      case prev / x => aux(x, prev)
    }
    aux("", this)
  }
  def /(s: String): Path = Path./(this, s)
}

object Path {

  case class /(prev: Path, value: String) extends Path
  case object Root extends Path

  val fromString: String => Path = _.split("/")
    .toList
    .filter(_.nonEmpty)
    .foldLeft(Root: Path)((xs, x) => /(xs, x))
}
