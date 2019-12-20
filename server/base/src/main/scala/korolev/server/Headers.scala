package korolev.server

import korolev.server.internal.Cookies

object Headers {

  final val ContentType = "content-type"
  final val SetCookie = "set-cookie"

  final val ContentTypeHtmlUtf8 =
    ContentType -> "text/html; charset=utf-8"

  def setCookie(cookie: String, value: String, path: String): (String, String) =
    SetCookie -> s"$cookie=$value; Path=$path"
}
