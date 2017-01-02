package korolev.server

import java.io.InputStream

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
sealed trait Response

object Response {

  case class Http(body: InputStream,
                  fileExtension: String,
                  deviceId: Option[String])
      extends Response

  case class WebSocket(publish: String => Unit,
                       subscribe: (String => Unit) => Unit,
                       destroyHandler: () => Unit)
      extends Response

}
