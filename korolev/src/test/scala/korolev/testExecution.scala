package korolev

import korolev.effect.{Effect, Reporter}
import korolev.internal.Scheduler

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object testExecution {

  private class RunNowExecutionContext extends ExecutionContext {
    def execute(runnable: Runnable): Unit = runnable.run()
    def reportFailure(cause: Throwable): Unit = cause.printStackTrace()
  }

  implicit val defaultExecutor: ExecutionContext = new RunNowExecutionContext()
}
