package korolev

import scala.annotation.switch
import scala.collection.mutable

package object internal {

  private[korolev]  def jsonCharAppend(sb: mutable.StringBuilder, c: Char, unicode: Boolean): Unit = {
    (c: @switch) match {
      case '"' => sb.append("\\\"")
      case '\\' => sb.append("\\\\")
      case '\b' => sb.append("\\b")
      case '\f' => sb.append("\\f")
      case '\n' => sb.append("\\n")
      case '\r' => sb.append("\\r")
      case '\t' => sb.append("\\t")
      case _ =>
        if (c < ' ' || (c > '~' && unicode)) sb.append("\\u%04x" format c.toInt)
        else sb.append(c)
    }
    ()
  }

  private[korolev] def jsonEscape(sb: mutable.StringBuilder, s: String, unicode: Boolean): Unit = {
    var i = 0
    val len = s.length
    while (i < len) {
      jsonCharAppend(sb, s.charAt(i), unicode)
      i += 1
    }
    ()
  }

}
