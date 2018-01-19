package korolev

import monix.eval.Task
import monix.execution.Scheduler

import scala.collection.mutable
import scala.concurrent.ExecutionContext

package object monixSupport {

  private val ecTaskInstanceCache =
    mutable.Map.empty[Scheduler, Async[Task]]

  private val schedulerTaskInstanceCache =
    mutable.Map.empty[ExecutionContext, Async[Task]]

  /**
    * Creates an Async instance for Monix Task.
    * Physically one instance per scheduler is maintained.
    */
  implicit def schedulerTaskAsync(implicit scheduler: Scheduler): Async[Task] =
    ecTaskInstanceCache.synchronized {
      ecTaskInstanceCache.getOrElseUpdate(scheduler, new TaskAsync()(scheduler))
    }

  /**
    * Creates an Async instance for Monix Task.
    * Physically one instance per execution context is maintained.
    */
  implicit def executionContextTaskAsync(implicit ec: ExecutionContext): Async[Task] =
    schedulerTaskInstanceCache.synchronized {
      schedulerTaskInstanceCache.getOrElseUpdate(ec, new TaskAsync()(Scheduler(ec)))
    }

}
