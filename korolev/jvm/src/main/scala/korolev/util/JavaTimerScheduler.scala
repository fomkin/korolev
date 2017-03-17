package korolev.util

import java.util.{Timer, TimerTask}

import korolev.Async
import korolev.Async.AsyncOps
import korolev.util.Scheduler.{Cancel, JobHandler}

import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds
import scala.util.Success

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
final class JavaTimerScheduler[F[+_]: Async] extends Scheduler[F] {

  private val timer = new Timer()
  private val async = Async[F]

  def scheduleOnce[T](delay: FiniteDuration)(job: => T): JobHandler[F, T] = {
    val promise = Async[F].promise[T]
    val task = new TimerTask {
      def run(): Unit = {
        val task = async.fork {
          val result = job // Execute a job
          promise.complete(Success(result))
        }
        task.run()
      }
    }
    timer.schedule(task, delay.toMillis)
    JobHandler(
      cancel = () => task.cancel,
      result = promise.future
    )
  }

  def schedule[U](interval: FiniteDuration)(job: => U): Cancel = {
    val task = new TimerTask {
      def run(): Unit = async.fork(job).run()
    }
    val millis = interval.toMillis
    timer.schedule(task, millis, millis)
    () => task.cancel
  }
}
