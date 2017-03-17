package korolev.util

import korolev.Async
import korolev.util.Scheduler.{Cancel, JobHandler}

import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds
import scala.scalajs.js.timers._
import scala.util.Try

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
final class JsTimersScheduler[F[+_]: Async] extends Scheduler[F] {

  private val async = Async[F]

  def scheduleOnce[T](delay: FiniteDuration)(job: => T): JobHandler[F, T] = {
    val promise = async.promise[T]
    val handle = setTimeout(delay) {
      promise.complete(Try(job))
    }
    JobHandler(
      cancel = () => clearTimeout(handle),
      result = promise.future
    )
  }

  def schedule[U](interval: FiniteDuration)(job: => U): Cancel = {
    val handle = setInterval(interval)(job)
    () => clearInterval(handle)
  }
}
