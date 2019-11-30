package korolev

import scala.concurrent.ExecutionContext

object testExecution {

  private class RunNowExecutionContext extends ExecutionContext {
    def execute(runnable: Runnable): Unit = runnable.run()
    def reportFailure(cause: Throwable): Unit = cause.printStackTrace()
  }

  implicit val defaultExecutor: ExecutionContext = new RunNowExecutionContext()
}
