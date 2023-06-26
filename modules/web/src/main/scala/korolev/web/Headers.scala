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

import java.util.Base64

object Headers {

  def basicAuthorization(userInfo: String): (String, String) = {
    val credentials = Base64.getEncoder.encodeToString(userInfo.getBytes)
    Authorization -> s"Basic $credentials"
  }

  final val Authorization = "Authorization"
  final val Host = "Host"
  final val Connection = "Connection"
  final val Upgrade = "Upgrade"
  final val ContentType = "Content-Type"
  final val ContentLength = "Content-Length"
  final val Cookie = "Cookie"
  final val SetCookie = "Set-Cookie"
  final val CacheControl = "Cache-Control"
  final val Pragma = "Pragma"
  final val SecWebSocketKey = "Sec-WebSocket-Key"
  final val SecWebSocketAccept = "Sec-WebSocket-Accept"
  final val TransferEncoding  = "Transfer-Encoding"
  final val SecWebSocketVersion = "Sec-WebSocket-Version"
  final val SecWebSocketProtocol = "Sec-WebSocket-Protocol"
  final val AcceptEncoding = "Accept-Encoding"

  final val SecWebSocketVersion13 = SecWebSocketVersion -> "13"
  final val TransferEncodingChunked = TransferEncoding -> "chunked"
  final val CacheControlNoCache = CacheControl -> "no-store, no-cache, must-revalidate"
  final val PragmaNoCache = Pragma -> "no-cache"
  final val ContentTypeTextUtf8 = ContentType -> "text/plain; charset=utf-8"
  final val ContentTypeHtmlUtf8 = ContentType -> "text/html; charset=utf-8"
  final val ConnectionUpgrade = Connection -> "Upgrade"
  final val UpgradeWebSocket = Upgrade -> "websocket"

  def setCookie(cookie: String, value: String, path: String, maxAge: Int): (String, String) =
    SetCookie -> s"$cookie=$value; Path=$path; Max-Age=$maxAge; SameSite=Lax; HttpOnly"

}
