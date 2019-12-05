package korolev

import korolev.execution.Scheduler
import Scheduler._
import korolev.effect.{Effect, Reporter}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object testExecution {

  private class RunNowExecutionContext extends ExecutionContext {
    def execute(runnable: Runnable): Unit = runnable.run()
    def reportFailure(cause: Throwable): Unit = cause.printStackTrace()
  }

  implicit val defaultExecutor: ExecutionContext = new RunNowExecutionContext()

  implicit def defaultScheduler[F[_]: Effect] = new Scheduler[F] {
    def scheduleOnce[T](delay: FiniteDuration)(job: => T)(implicit r: Reporter): JobHandler[F, T] = ???
    def schedule[U](interval: FiniteDuration)(job: => U)(implicit r: Reporter): Cancel = ???
  }
}
