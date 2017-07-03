package korolev.akkahttp.util

import akka.actor.PoisonPill
import akka.stream.actor.{ActorPublisher, ActorPublisherMessage}

import scala.collection.immutable.Queue
import scala.reflect.{ClassTag, classTag}

class QueuingActorPublisher[T: ClassTag] extends ActorPublisher[T] {

  override def receive: Receive = {
    case ActorPublisherMessage.Request(demand) =>
      if (!started) {
        started = true
      }

      satisfyDemand(demand)
      stopIfFinishedAndEmpty()

    case value: T@unchecked if !finished && classTag[T].runtimeClass.isInstance(value) =>
      if (isActive && totalDemand > 0) {
        if (queue.nonEmpty) {
          enqueue(value)
          satisfyDemand(totalDemand)
        } else {
          onNext(value)
        }
      } else {
        enqueue(value)
      }

      stopIfFinishedAndEmpty()

    case QueuingActorPublisher.Finish =>
      finished = true
      stopIfFinishedAndEmpty()
  }

  private var started: Boolean = false
  private var finished: Boolean = false

  private var queue: Queue[T] = Queue.empty

  private def enqueue(value: T): Unit = {
    queue = queue.enqueue(value)
  }

  private def satisfyDemand(demand: Long): Unit = {
    if (queue.nonEmpty) {
      val (values, rest) = queue.splitAt(demand.toInt)
      values.foreach(onNext)
      queue = rest
    }
  }

  private def stopIfFinishedAndEmpty(): Unit =
    if (!isCompleted) {
      if (finished && queue.isEmpty) {
        onComplete()
        self ! PoisonPill
      }
    }

}

object QueuingActorPublisher {

  case object Finish

}

