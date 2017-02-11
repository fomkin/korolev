package korolev.server

import korolev.Router

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
case class Request(
  path: Router.Path,
  params: Map[String, String],
  cookie: String => Option[String],
  body: String
)
