package korolev

import korolev.util.{JsTimersScheduler, Scheduler}

import scala.language.higherKinds

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
object execution {
  implicit val defaultExecutor =
    scala.scalajs.concurrent.JSExecutionContext.queue

  implicit def defaultScheduler[F[+_]: Async]: Scheduler[F] =
    new JsTimersScheduler[F]
}
