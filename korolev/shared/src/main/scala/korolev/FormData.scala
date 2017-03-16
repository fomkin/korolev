package korolev

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import korolev.FormData.Entry

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
final case class FormData(content: Seq[Entry]) {

  def text(name: String): String = {
    val array = bytes(name).array()
    new String(array, StandardCharsets.UTF_8)
  }

  def bytes(name: String): ByteBuffer = bytesOpt(name).get

  def bytesOpt(name: String): Option[ByteBuffer] = {
    apply(name).map(_.content)
  }

  def contentType(name: String): Option[String] = {
    apply(name) flatMap { entry =>
      entry.headers collectFirst {
        case (k, v) if k.toLowerCase == "content-type" => v
      }
    }
  }

  def apply(name: String): Option[Entry] =
    content.find(_.name == name)
}

object FormData {

  case class Entry(
      name: String,
      content: ByteBuffer,
      headers: Seq[(String, String)]
  )
}
