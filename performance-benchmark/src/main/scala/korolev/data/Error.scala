package korolev.data

sealed trait Error

object Error {
  case class ArbitraryThrowable(e: Throwable) extends Error
  case class InvalidHttpStatusCodeForPage(code: Int) extends Error
  case class InvalidHttpStatusCodeForWS(code: Int) extends Error
  case object SessionIdNotDefined extends Error
  case object DeviceIdNotDefined extends Error
}
