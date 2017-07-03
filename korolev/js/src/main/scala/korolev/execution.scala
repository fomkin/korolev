package korolev

import korolev.util.{JsTimersScheduler, Scheduler}

object execution {
  implicit val defaultExecutor =
    scala.scalajs.concurrent.JSExecutionContext.queue

  implicit def defaultScheduler[F[+_]: Async]: Scheduler[F] =
    new JsTimersScheduler[F]
}
