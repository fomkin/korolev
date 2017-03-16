import scala.concurrent.ExecutionContext

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
class RunNowExecutionContext extends ExecutionContext {
  def execute(runnable: Runnable): Unit = runnable.run()
  def reportFailure(cause: Throwable): Unit = cause.printStackTrace()
}

object RunNowExecutionContext {
  implicit val instance: ExecutionContext =
    new RunNowExecutionContext()
}
