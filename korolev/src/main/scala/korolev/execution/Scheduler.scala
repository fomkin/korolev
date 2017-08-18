package korolev.execution

import korolev.Async

import scala.annotation.implicitNotFound
import scala.concurrent.duration.FiniteDuration

@implicitNotFound("Scheduler for ${F} is not found. Ensure that it is passed to the scope (import korolev.execution.defaultScheduler)")
abstract class Scheduler[F[+ _] : Async] {
  import Scheduler._
  def scheduleOnce[T](delay: FiniteDuration)(job: => T): JobHandler[F, T]
  def schedule[U](interval: FiniteDuration)(job: => U): Cancel
}

object Scheduler {

  def apply[F[+_]: Scheduler]: Scheduler[F] = implicitly[Scheduler[F]]

  type Cancel = () => Unit

  case class JobHandler[F[+_]: Async, +T](
    cancel: Cancel,
    result: F[T]
  )
}

