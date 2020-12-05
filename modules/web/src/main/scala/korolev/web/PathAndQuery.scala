package korolev.web

import java.net.{URLDecoder, URLEncoder}
import java.nio.charset.StandardCharsets
import scala.annotation.tailrec

sealed trait PathAndQuery {

  import PathAndQuery._

  private[this] def encode(value: String): String = {
    URLEncoder.encode(value, StandardCharsets.UTF_8)
  }

  def startsWith(value: String): Boolean = {
    @tailrec
    def aux(pq: PathAndQuery): Boolean = pq match {
      case :&(prev, _)                  => aux(prev)
      case prev :? _                    => aux(prev)
      case /(Root | RelativeRoot, path) => path == value
      case /(prev, _)                   => aux(prev)
      case _                            => false
    }

    aux(this)
  }

  def endsWith(value: String): Boolean = {
    @tailrec
    def aux(pq: PathAndQuery): Boolean = pq match {
      case :&(prev, _) => aux(prev)
      case prev :? _   => aux(prev)
      case /(_, path)  => path == value
      case _           => false
    }

    aux(this)
  }

  def mkString: String = {
    @tailrec
    def aux(pq: PathAndQuery, path: Seq[String], query: String): String = {
      pq match {
        case Root                => "/" + path.mkString("/") + query
        case RelativeRoot        => path.mkString("/") + query
        case :&(prev, =?=(k, v)) => aux(prev, path, s"&${encode(k)}=${encode(v)}" + query)
        case head :? (k =?= v)   => aux(head, path, s"?${encode(k)}=${encode(v)}" + query)
        case /(head, segment)    => aux(head, segment +: path, query)
        case =?=(_, _)           => "/" + path.mkString("/") + query
      }
    }

    aux(this, Seq.empty, "")
  }

  def param(name: String): Option[String] = {
    @tailrec
    def aux(pq: PathAndQuery): Option[String] = {
      pq match {
        case _: Path                       => None
        case :&(_, =?=(k, v)) if k == name => Some(v)
        case :&(prev, _)                   => aux(prev)
        case _ :? (k =?= v) if k == name   => Some(v)
        case _ :? _                        => None
      }
    }

    aux(this)
  }

  def withParam(key: String, value: String): PathAndQuery = {
    this match {
      case path: Path =>
        path :? =?=(key, value)
      case query: Query =>
        query :& =?=(key, value)
    }
  }

  def withParam(key: String, value: Option[String]): PathAndQuery = {
    withParam(key, value.getOrElse(""))
  }

  def withParams(params: Option[String]): PathAndQuery = {
    parseParams(params).foldLeft(this) {
      case (pq, (key, value)) =>
        pq.withParam(key, value)
    }
  }
}

sealed trait Query extends PathAndQuery {
  import korolev.web.PathAndQuery._

  def :&(next: =?=): Query = {
    new :&(this, next)
  }
}

sealed trait Path extends PathAndQuery {

  import PathAndQuery._

  def /(s: String): Path = PathAndQuery./(this, s)

  def :?(next: =?=): Query = {
    new :?(this, next)
  }

  @tailrec
  private[this] def pathRoot(path: Path): Path = path match {
    case /(Root, _)         => Root
    case /(RelativeRoot, _) => RelativeRoot
    case /(prev, _)         => pathRoot(prev)
    case _                  => throw new Exception("?== should not be reachable")
  }

  def reverse: Path = {
    @tailrec
    def reverse(path: Path, result: Path): Path = {
      path match {
        case /(Root | RelativeRoot, path) => result / path
        case /(p, path)                   => reverse(p, result / path)
        case Root                         => Root
        case RelativeRoot                 => RelativeRoot
      }
    }

    reverse(this, pathRoot(this))
  }

  def ++(tail: Path): Path = {
    @tailrec
    def aux(result: Path, other: Path): Path = {
      other match {
        case /(RelativeRoot | Root, path) => result / path
        case /(p, path)                   => aux(result / path, p)
        case RelativeRoot                 => tail
        case Root                         => tail
      }
    }

    aux(this, tail.reverse)
  }
}

object PathAndQuery {
  case object Root extends Path
  case object RelativeRoot extends Path

  final case class /(prev: Path, value: String) extends Path

  object / {
    def apply(prev: Path, value: String): / =
      new /(prev, value)

    @tailrec
    def unapply(pq: PathAndQuery): Option[(Path, String)] = pq match {
      case slash: /   => Some(slash.prev -> slash.value)
      case path :? _  => unapply(path)
      case query :& _ => unapply(query)
      case _          => None
    }
  }

  final case class =?=(k: String, v: String) extends Query
  final case class :&(prev: Query, next: (=?=)) extends Query
  final case class :?(path: Path, next: (=?=)) extends Query

  object *& {
    def unapply(params: Map[String, String]): Some[(Map[String, String], Map[String, String])] =
      Some((params, params))
  }

  object :?* {
    def unapply(pq: PathAndQuery): Some[(Path, Map[String, String])] = {
      @tailrec
      def aux(pq: PathAndQuery, query: Map[String, String]): (Path, Map[String, String]) = pq match {
        case :&(prev, =?=(k, v)) => aux(prev, query + (k -> v))
        case path :? (k =?= v)   => (path, query + (k -> v))
        case path: /             => (path, query)
        case Root                => (Root, query)
        case RelativeRoot        => (RelativeRoot, query)
        case _: =?=              => throw new Exception("?== should not be reachable")
      }

      Some(aux(pq, Map.empty))
    }
  }

  /**
    * Math required path parameter
    *
    * @param name - name of required path parameter
    */
  abstract class QueryParam(name: String) {
    def unapply(params: Map[String, String]): Option[String] =
      params.get(name)
  }

  type QP = QueryParam

  /**
    * Math optional path parameter
    *
    * @param name - name of Optional path parameter
    */
  abstract class OptionQueryParam(name: String) {

    def unapply(params: Map[String, String]): Option[Option[String]] =
      Option(params.get(name))
  }

  type OQP = OptionQueryParam

  def fromString(raw: String): PathAndQuery = {
    val pathAndQuery = raw.split('?')
    val params: Seq[(String, String)] = parseParams(pathAndQuery.lift(1))

    val first: Path = if (raw.startsWith("/")) Root else RelativeRoot

    pathAndQuery.headOption match {
      case None =>
        first
      case Some(rawPath) =>
        val path = rawPath
          .split("/")
          .toList
          .filter(_.nonEmpty)
          .foldLeft(first)((xs, x) => PathAndQuery./(xs, x))

        params.foldLeft[PathAndQuery](path) {
          case (result, (key, value)) =>
            result.withParam(key, value)
        }
    }
  }

  def parseParams(params: Option[String]): Seq[(String, String)] = {
    params match {
      case None =>
        Seq.empty
      case Some(query) =>
        query
          .split('&')
          .map { xs =>
            val parts: Array[String] = xs.split('=')
            (decode(parts.head), parts.lift(1).map(decode).getOrElse(""))
          }
    }
  }

  private[this] def decode(value: String): String = {
    URLDecoder.decode(value, StandardCharsets.UTF_8)
  }
}