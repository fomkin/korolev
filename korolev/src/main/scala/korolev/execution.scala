package korolev

import java.util.concurrent.Executors

import korolev.util.{JavaTimerScheduler, Scheduler}

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext
import scala.language.higherKinds
import scala.reflect.ClassTag

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
object execution {

  private val schedulerCache = TrieMap.empty[Any, Any]

  implicit val defaultExecutor = ExecutionContext.
    fromExecutorService(Executors.newWorkStealingPool())

  implicit def defaultScheduler[F[+_]: Async]: Scheduler[F] = {
    val async = Async[F]
    val scheduler = schedulerCache.getOrElseUpdate(async, new JavaTimerScheduler[F])
    scheduler.asInstanceOf[Scheduler[F]]
  }
}
