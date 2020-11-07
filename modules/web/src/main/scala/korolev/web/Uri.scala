package korolev.web

import java.net.{URLDecoder, URLEncoder}
import java.nio.charset.StandardCharsets

case class Uri(path: Path, rawQueryString: Option[String]) {
  lazy val params: Map[String, String] = rawQueryString match {
    case None =>
      Map.empty[String, String]
    case Some(query) =>
      query
        .split('&')
        .map { xs =>
          val Array(k, v) = xs.split('=')
          (URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8))
        }
        .toMap
  }

  def param(name: String): Option[String] =
    params.get(name)

  def withParam(name: String, value: Option[String]): Uri = {
    value match {
      case Some(v) =>
        withParam(name, v)
      case None =>
        this
    }
  }

  def withParam(name: String, value: String): Uri = {
    val ek = URLEncoder.encode(name, StandardCharsets.UTF_8)

    rawQueryString match {
      case None =>
        if (value.isEmpty) {
          copy(rawQueryString = Some(ek))
        } else {
          val ev = URLEncoder.encode(value, StandardCharsets.UTF_8)
          copy(rawQueryString = Some(s"$ek=$ev"))
        }
      case Some(query) =>
        if (value.isEmpty) {
          copy(rawQueryString = Some(s"$query&$ek"))
        } else {
          val ev = URLEncoder.encode(value, StandardCharsets.UTF_8)
          copy(rawQueryString = Some(s"${query}&$ek=$ev"))
        }
    }
  }

  def mkString: String = {
    rawQueryString match {
      case None =>
        s"${path.mkString}"
      case Some(query) =>
        s"${path.mkString}?$query"
    }
  }
}

object Uri {
  def fromString(source: String): Uri = {
    val splited = source.split('?')

    if (splited.length == 1) {
      Uri(Path.fromString(splited(0)), None)
    } else if (splited.length == 2) {
      Uri(Path.fromString(splited(0)), Option(splited(1)).filter(_.isBlank))
    } else {
      Uri(Path.Root, None)
    }
  }

  def apply(path: Path): Uri = {
    Uri(path, None)
  }

  object UriRoot extends Uri(Path.Root, None)

  object :? {
    def unapply(uri: Uri): Option[(Path, Map[String, String])] =
      Some((uri.path, uri.params))
  }

  object +& {
    def unapply(params: Map[String, String]): Option[(Map[String, String], Map[String, String])] =
      Some((params, params))
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

  /**
    * Math optional path parameter
    *
    * @param name - name of Optional path parameter
    */
  abstract class OptionQueryParam(name: String) {

    def unapply(params: Map[String, String]): Option[Option[String]] =
      Option(params.get(name))
  }

  /**
    * Simplification of backward compatibility
    */
  def apply[S, R](pf: PartialFunction[Path, S => R] = PartialFunction.empty,
                  pp: PartialFunction[Uri, S => R] = PartialFunction.empty): PartialFunction[Uri, S => R] = {
    case uri: Uri if pf.isDefinedAt(uri.path) =>
      pf(uri.path)
    case uri: Uri if pp.isDefinedAt(uri) =>
      pp(uri)
  }

  implicit def pathToUri(path: Path): Uri = {
    Uri.apply(path)
  }
}
