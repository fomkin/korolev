package korolev

trait Reporter {
  implicit val Implicit: Reporter = this
  def error(error: Throwable): Unit
  def error(message: String, cause: Throwable): Unit
  def error(message: String): Unit
  def warning(message: Throwable): Unit
  def warning(message: String, cause: Throwable): Unit
  def warning(message: String): Unit
  def info(message: String): Unit
}

object Reporter {

  final object PrintReporter extends Reporter {
    def error(error: Throwable): Unit = {
      print("[ERROR] ")
      error.printStackTrace(System.out)
    }
    def error(message: String, error: Throwable): Unit = {
      print(s"[ERROR] $message")
      error.printStackTrace(System.out)
    }
    def error(message: String): Unit = {
      println(s"[ERROR] $message")
    }
    def warning(warning: Throwable): Unit = {
      print("[WARNING] ")
      warning.printStackTrace(System.out)
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
