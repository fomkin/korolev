package korolev.effect

import scala.concurrent.ExecutionContext

object RunNowExecutionContext extends ExecutionContext {
  implicit val Implicit: ExecutionContext = this
  def execute(runnable: Runnable): Unit = runnable.run()
  def reportFailure(cause: Throwable): Unit = cause.printStackTrace()
}