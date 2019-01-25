package korolev.akkahttp.util

import akka.actor.ActorSystem
import akka.event.{LogSource, Logging}
import korolev.Reporter

final class LoggingReporter(actorSystem: ActorSystem) extends Reporter {

  private implicit val logSource: LogSource[LoggingReporter] = new LogSource[LoggingReporter] {
    def genString(t: LoggingReporter): String = "korolev"
  }

  private val log = Logging(actorSystem, this)

  def error(message: String, cause: Throwable): Unit = log.error(cause, message)
  def error(message: String): Unit = log.error(message)
  def warning(message: String, cause: Throwable): Unit = log.warning(s"$message: {}", cause)
  def warning(message: String): Unit = log.warning(message)
  def info(message: String): Unit = log.info(message)
}
