package korolev.blazeServer.util

import java.util.{Timer, TimerTask}

import korolev.Async

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds
import scala.util.Success

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
object ExecutionContextScheduler {

  private val timer = new Timer()

  type Cancel = () => Unit

  case class JobHandler[F[+_]: Async](
    cancel: Cancel,
    result: F[Unit]
  )

  def scheduleOnce[F[+_]: Async, U]
      (delay: FiniteDuration)
      (job: => U)
      (implicit ec: ExecutionContext): JobHandler[F] = {
    val promise = Async[F].promise[Unit]
    val task = new TimerTask {
      def run(): Unit = ec.execute {
        new Runnable {
          def run(): Unit = {
            job // Execute a job
            promise.complete(Success(()))
          }
        }
      }
    }
    timer.schedule(task, delay.toMillis)
    JobHandler(
      cancel = () => task.cancel,
      result = promise.future
    )
  }

  def schedule[U](interval: FiniteDuration)(job: => U)(implicit ec: ExecutionContext): Cancel = {
    val task = new TimerTask {
      def run(): Unit = ec.execute {
        new Runnable {
          def run(): Unit = job // Execute a job
        }
      }
    }
    val millis = interval.toMillis
    timer.schedule(task, millis, millis)
    () => task.cancel
  }
}
