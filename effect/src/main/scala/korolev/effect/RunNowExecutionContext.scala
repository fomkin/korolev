package korolev.effect

import scala.concurrent.ExecutionContext

object RunNowExecutionContext extends ExecutionContext {
  def execute(runnable: Runnable): Unit = runnable.run()
  def reportFailure(cause: Throwable): Unit = cause.printStackTrace()
}