package korolev.util

import korolev.Async
import korolev.util.Scheduler.{Cancel, JobHandler}

import scala.annotation.implicitNotFound
import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
@implicitNotFound("Scheduler for ${F} is not found. Ensure that it is passed to the scope (import korolev.execution.defaultScheduler)")
abstract class Scheduler[F[+ _] : Async] {
  def scheduleOnce[T](delay: FiniteDuration)(job: => T): JobHandler[F, T]
  def schedule[U](interval: FiniteDuration)(job: => U): Cancel
}

object Scheduler {

  type Cancel = () => Unit

  case class JobHandler[F[+_]: Async, +T](
    cancel: Cancel,
    result: F[T]
  )
}

