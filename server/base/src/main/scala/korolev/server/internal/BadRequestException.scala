package korolev.server.internal

private[korolev] case class BadRequestException(message: String)
  extends Throwable(message)
