package korolev.server

import java.nio.charset.StandardCharsets

sealed trait Response

object Response {

  case class Http(status: Status,
                  body: Option[Array[Byte]] = None,
                  headers: Seq[(String, String)] = Seq.empty)
      extends Response

  object Http {
    def apply(status: Status, message: String): Http = {
      val bytes = message.getBytes(StandardCharsets.UTF_8)
      Http(status, Some(bytes))
    }
  }

  case class WebSocket(publish: String => Unit,
                       subscribe: (String => Unit) => Unit,
                       destroyHandler: () => Unit)
      extends Response

  sealed trait Status {
    def code: Int
    def phrase: String
  }

  object Status {
    case object Ok extends Status {
      val code = 200
      val phrase = "OK"
    }
    case object BadRequest extends Status {
      val code = 400
      val phrase = "Bad Request"
    }
    case object Gone extends Status {
      val code = 410
      val phrase = "Gone"
    }
  }
}
