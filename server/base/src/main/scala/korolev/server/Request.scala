package korolev.server

import java.nio.ByteBuffer

import korolev.Router

case class Request(
  path: Router.Path,
  params: Map[String, String],
  cookie: String => Option[String],
  headers: Seq[(String, String)],
  body: ByteBuffer
)
