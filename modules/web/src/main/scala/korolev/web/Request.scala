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

package korolev.web

import java.net.{URLDecoder, URLEncoder}
import java.nio.charset.StandardCharsets

final case class Request[Body](method: Request.Method,
                               path: Path,
                               headers: Seq[(String, String)],
                               contentLength: Option[Long],
                               body: Body,
                               renderedParams: String = null,
                               renderedCookie: String = null)
    extends Request.Head {

  private lazy val parsedParams =
    if (renderedParams == null || renderedParams.isEmpty) Map.empty[String, String]
    else renderedParams
      .split('&')
      .map { xs =>
        val Array(k, v) = xs.split('=')
        (URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8))
      }
      .toMap

  private lazy val parsedCookie =
    if (renderedCookie == null || renderedCookie.isEmpty) Map.empty[String, String]
    else renderedCookie
      .split(';')
      .map { xs =>
        val Array(k, v) = xs.split('=')
        (URLDecoder.decode(k.trim, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8))
      }
      .toMap

  def param(name: String): Option[String] =
    parsedParams.get(name)

  def cookie(name: String): Option[String] =
    parsedCookie.get(name)

  def header(header: String): Option[String] =
    headers.collectFirst {
      case (k, v) if k.equalsIgnoreCase(header) => v
    }

  def withParam(name: String, value: String): Request[Body] = {
    val ek = URLEncoder.encode(name, StandardCharsets.UTF_8)
    if (renderedParams == null || renderedParams.isEmpty) {
      if (value.isEmpty) copy(renderedParams = ek) else {
        val ev = URLEncoder.encode(value, StandardCharsets.UTF_8)
        copy(renderedParams = s"$ek=$ev")
      }
    } else {
      if (value.isEmpty) copy(renderedParams = s"$renderedParams&$ek") else {
        val ev = URLEncoder.encode(value, StandardCharsets.UTF_8)
        copy(renderedParams = s"${renderedParams}&$ek=$ev")
      }
    }
  }

  def withCookie(name: String, value: String): Request[Body] = {
    val ek = URLEncoder.encode(name, StandardCharsets.UTF_8)
    val ev = URLEncoder.encode(value, StandardCharsets.UTF_8)
    if (renderedCookie == null || renderedCookie.isEmpty) {
      copy(renderedCookie = s"$ek=$ev")
    } else {
      copy(renderedCookie = s"${renderedCookie};$ek=$ev")
    }
  }

  def withHeader(key: String, value: String): Request[Body] =
    copy(headers = (key, value) +: headers)

  def withHeader(header: (String, String)): Request[Body] =
    copy(headers = header +: headers)

  def withHeaders(xs: (String, String)*): Request[Body] =
    copy(headers = xs ++: headers)
}

object Request {

  sealed trait Head {

    def method: Method

    def path: Path

    def param(name: String): Option[String]

    def cookie(name: String): Option[String]

    def header(header: String): Option[String]
  }

  sealed abstract class Method(val value: String)

  object Method {

    final val All = Set(Post, Get, Put, Delete, Options, Head, Trace, Connect)

    def fromString(method: String): Method =
      method match {
        case "POST" => Post
        case "GET" => Get
        case "PUT" => Put
        case "DELETE" => Delete
        case "OPTIONS" => Options
        case "HEAD" => Head
        case "TRACE" => Trace
        case "CONNECT" => Connect
        case _ => Unknown(method)
      }

    case object Post extends Method("POST")
    case object Get extends Method("GET")
    case object Put extends Method("PUT")
    case object Delete extends Method("DELETE")
    case object Options extends Method("OPTIONS")
    case object Head extends Method("HEAD")
    case object Trace extends Method("TRACE")
    case object Connect extends Method("CONNECT")
    case class Unknown(override val value: String) extends Method(value)
  }
}
