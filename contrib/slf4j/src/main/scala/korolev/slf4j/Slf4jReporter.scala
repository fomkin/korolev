package korolev.slf4j

import korolev.Reporter
import org.slf4j.LoggerFactory

object Slf4jReporter extends Reporter {

  // All messages will be emited from one source.
  // It's unnecessary to show Korolev internals to enduser.
  private val logger = LoggerFactory.getLogger("Korolev")

  def error(message: String, cause: Throwable): Unit = logger.error(message, cause)
  def error(message: String): Unit = logger.error(message)
  def warning(message: String, cause: Throwable): Unit = logger.warn(message, cause)
  def warning(message: String): Unit = logger.warn(message)
  def info(message: String): Unit = logger.info(message)
}
