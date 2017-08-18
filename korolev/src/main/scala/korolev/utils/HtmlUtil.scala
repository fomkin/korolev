package korolev.utils

object HtmlUtil {

  def camelCaseToSnakeCase(value: String, startIndex: Int): String = {
    val sb = new StringBuilder()
    camelCaseToSnakeCase(sb, value, startIndex)
    sb.mkString
  }

  def camelCaseToSnakeCase(value: String, prefix: Char, startIndex: Int): String = {
    val sb = new StringBuilder()
    sb.append(prefix)
    camelCaseToSnakeCase(sb, value, startIndex)
    sb.mkString
  }

  def camelCaseToSnakeCase(sb: StringBuilder, value: String, startIndex: Int): Unit = {
    var i = startIndex
    while (i < value.length) {
      val char = value(i)
      if (Character.isUpperCase(char)) {
        sb.append('-')
        sb.append(Character.toLowerCase(char))
      } else {
        sb.append(char)
      }
      i += 1
    }
  }
}
