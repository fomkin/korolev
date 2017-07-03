package korolev

import korolev.util.Scheduler
import korolev.util.Scheduler.{Cancel, JobHandler}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object testExecution {

  private class RunNowExecutionContext extends ExecutionContext {
    def execute(runnable: Runnable): Unit = runnable.run()
    def reportFailure(cause: Throwable): Unit = cause.printStackTrace()
  }

  implicit val defaultExecutor: ExecutionContext = new RunNowExecutionContext()

  implicit def defaultScheduler[F[+_]: Async] = new Scheduler[F] {
    def scheduleOnce[T](delay: FiniteDuration)(job: => T): JobHandler[F, T] = ???
    def schedule[U](interval: FiniteDuration)(job: => U): Cancel = ???
  }
}
