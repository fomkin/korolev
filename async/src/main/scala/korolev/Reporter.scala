package korolev

/**
  * Korolev INTERNAL reporting subsystem.
  * Do not use it in application code.
  */
trait Reporter {
  implicit val Implicit: Reporter = this
  def error(message: String, cause: Throwable): Unit
  def error(message: String): Unit
  def warning(message: String, cause: Throwable): Unit
  def warning(message: String): Unit
  def info(message: String): Unit
}

object Reporter {

  /**
    * Default STDOUT reporting
    */
  final object PrintReporter extends Reporter {
    def error(message: String, error: Throwable): Unit = {
      print(s"[ERROR] $message")
      error.printStackTrace(System.out)
    }
    def error(message: String): Unit = {
      println(s"[ERROR] $message")
    }
    def warning(message: String, warning: Throwable): Unit = {
      print(s"[WARNING] $message")
      warning.printStackTrace(System.out)
    }
    def warning(message: String): Unit = {
      println(s"[WARNING] $message")
    }
    def info(message: String): Unit = {
      println(s"[INFO] $message")
    }
  }
}
