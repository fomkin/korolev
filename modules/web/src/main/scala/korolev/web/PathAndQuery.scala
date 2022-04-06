package korolev.web

import java.net.{URLDecoder, URLEncoder}
import scala.annotation.tailrec
import scala.util.hashing.MurmurHash3

sealed trait PathAndQuery {

  import PathAndQuery._

  def asPath: Path = {
    @tailrec
    def aux(pq: PathAndQuery): Path = {
      pq match {
        case q: :&   => aux(q.prev)
        case q: :?   => q.path
        case path: / => path
        case Root    => Root
      }
    }
    aux(this)
  }

  def startsWith(value: String): Boolean = {
    @tailrec
    def aux(pq: PathAndQuery): Boolean = pq match {
      case :&(prev, _) => aux(prev)
      case prev :? _   => aux(prev)
      case p: / =>
        if (p.prev == Root) {
          p.value == value
        } else {
          aux(p.prev)
        }
      case _ => false
    }

    aux(this)
  }

  def endsWith(value: String): Boolean = {
    @tailrec
    def aux(pq: PathAndQuery): Boolean = {
      pq match {
        case q: :& => aux(q.prev)
        case q: :? => aux(q.path)
        case p: /  => p.value == value
        case _     => false
      }
    }

    aux(this)
  }

  def mkString(builder: StringBuilder): Unit = {
    def encodeParam(key: String, value: String, tail: List[String]): List[String] = {
      if (value.nonEmpty) encode(key) :: "=" :: encode(value) :: tail
      else encode(key) :: tail
    }
    @tailrec
    def aux(pq: PathAndQuery, result: List[String]): List[String] = {
      pq match {
        case value: /  => aux(value.prev, "/" :: value.value :: result)
        case value: :& => aux(value.prev, "&" :: encodeParam(value.next._1, value.next._2, result))
        case value: :? => aux(value.path, "?" :: encodeParam(value.next._1, value.next._2, result))
        case Root => if (result.isEmpty) "/" :: Nil else result
      }
    }

    aux(this, Nil).foreach { chunk =>
      builder.appendAll(chunk)
    }
  }

  def mkString: String = {
    val sb = new StringBuilder()
    mkString(sb)
    sb.mkString
  }

  def param(name: String): Option[String] = {
    @tailrec
    def aux(pq: PathAndQuery): Option[String] = {
      pq match {
        case _: Path                    => None
        case :&(_, (k, v)) if k == name => Some(v)
        case :&(prev, _)                => aux(prev)
        case _ :? tpl if tpl._1 == name => Some(tpl._2)
      }
    }

    aux(this)
  }

  def withParam(key: String, value: String): PathAndQuery = {
    this match {
      case path: Path =>
        :?(path, (key, value))
      case query: Query =>
        :&(query, (key, value))
    }
  }

  def withParam(key: String, value: Option[String]): PathAndQuery = {
    withParam(key, value.getOrElse(""))
  }

  def withParams(params: Option[String]): PathAndQuery = {
    params match {
      case None =>
        this
      case Some(raw) =>
        parseParams(this, raw)
    }
  }

  def withParams(params: Map[String, String]): PathAndQuery = {
    params.foldLeft(this) {
      case (pq, (key, value)) =>
        pq.withParam(key, value)
    }
  }
}

sealed trait Query extends PathAndQuery {
  import korolev.web.PathAndQuery._

  def :&(next: (String, String)): Query = {
    new :&(this, next)
  }
}

sealed trait Path extends PathAndQuery {

  import PathAndQuery._

  def /(s: String): Path = PathAndQuery./(this, s)

  def :?(next: (String, String)): Query = {
    new :?(this, next)
  }

  def reverse: Path = {
    @tailrec
    def reverse(path: Path, result: Path): Path = {
      path match {
        case p: / =>
          if (p.prev == Root) {
            PathAndQuery./(result, p.value)
          } else {
            reverse(p.prev, PathAndQuery./(result, p.value))
          }
        case Root => Root
      }
    }

    reverse(this, Root)
  }

  def ++(tail: Path): Path = {
    @tailrec
    def aux(result: Path, other: Path): Path = {
      other match {
        case p: / =>
          if (p.prev == Root) {
            PathAndQuery./(result, p.value)
          } else {
            aux(result / p.value, p.prev)
          }
        case Root => tail
      }
    }

    aux(this, tail.reverse)
  }

  def ++(tail: PathAndQuery): PathAndQuery = {
    @tailrec
    def helper(pq: PathAndQuery, query: List[(String, String)]): (Path, List[(String, String)]) = pq match {
      case q: :& => helper(q.prev, q.next :: query)
      case q: :? => (q.path, q.next :: query)
      case p: / => (p, query)
      case Root => (Root, query)
    }

    val (tailPath, tailQueryReversed) = helper(tail, List.empty)
    tailQueryReversed.foldLeft[PathAndQuery](this ++ tailPath) {
      case (pq, (key, value)) => pq.withParam(key, value)
    }
  }
}

object PathAndQuery {
  case object Root extends Path

  final case class /(prev: Path, value: String) extends Path

  object / {
    def apply(prev: Path, value: String): / =
      new /(prev, value)

    @tailrec
    def unapply(pq: PathAndQuery): Option[(Path, String)] = pq match {
      case slash: / => Some(slash.prev -> slash.value)
      case q: :&    => unapply(q.prev)
      case q: :?    => unapply(q.path)
      case _        => None
    }
  }

  final case class :&(prev: Query, next: (String, String)) extends Query
  final case class :?(path: Path, next: (String, String)) extends Query

  object *& {
    def unapply(params: Map[String, String]): Some[(Map[String, String], Map[String, String])] =
      Some((params, params))
  }

  object :?* {
    def unapply(pq: PathAndQuery): Some[(Path, Map[String, String])] = {
      @tailrec
      def aux(pq: PathAndQuery, query: Map[String, String]): (Path, Map[String, String]) = pq match {
        case q: :& => aux(q.prev, query + q.next)
        case q: :? => (q.path, query + q.next)
        case p: /  => (p, query)
        case Root  => (Root, query)
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
    if (raw.isEmpty) {
      Root
    } else {
      raw.indexOf('?') match {
        case -1 =>
          parsePath(raw)
        case qIndex =>
          val path = parsePath(raw.substring(0, qIndex))

          if (qIndex + 1 != raw.length) {
            parseParams(path, raw.substring(qIndex + 1, raw.length))
          } else {
            path
          }
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

  private[web] def parsePath(raw: String): Path = {
    raw
      .split('/')
      .foldLeft[Path](Root)((xs, x) =>
        if (x.isEmpty) {
          xs
        } else {
          PathAndQuery./(xs, x)
        })
  }

  private[web] def parseParams(path: PathAndQuery, raw: String): PathAndQuery = {
    raw
      .split('&')
      .foldLeft[PathAndQuery](path) { (acc, raw) =>
        raw.indexOf('=') match {
          case -1 =>
            acc.withParam(decode(raw), "")
          case i =>
            acc.withParam(decode(raw.substring(0, i)), decode(raw.substring(i + 1, raw.length)))
        }
      }
  }

  private[web] def decode(value: String): String = {
    URLDecoder.decode(value, "UTF-8")
  }

  private[web] def encode(value: String): String = {
    URLEncoder.encode(value, "UTF-8")
  }
}
