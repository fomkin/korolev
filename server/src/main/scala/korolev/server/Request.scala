package korolev.server

import java.nio.ByteBuffer

import korolev.Router

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
case class Request(
  path: Router.Path,
  params: Map[String, String],
  cookie: String => Option[String],
  headers: Seq[(String, String)],
  body: ByteBuffer
)
