package korolev

import java.util.concurrent.ConcurrentLinkedQueue

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

trait Dux[State] {
  def state: State
  def subscribe[U](f: State => U): Dux.Unsubscribe
  def apply(action: State => State): Future[Unit]
}

object Dux {

  type Unsubscribe = () => Unit
  type Transition[State] = State => State

  def apply[State](
      initialState: State)(
      implicit ec: ExecutionContext): Dux[State] = {

    new Dux[State] {

      val queue = new ConcurrentLinkedQueue[(Promise[Unit], Transition[State])]

      @volatile var _state = initialState
      @volatile var subscribers = List.empty[State => _]
      @volatile var inProgress = false

      def apply(transition: Transition[State]): Future[Unit] = {
        def executeNext(): Unit = {
          val (promise, transition) = queue.poll()
          Future(transition(_state)) onComplete {
            case Success(newState) =>
              _state = newState
              subscribers.foreach(f => f(newState))
              promise.success(())
              if (queue.isEmpty) inProgress = false
              else executeNext()
            case Failure(exception) =>
              promise.failure(exception)
              if (queue.isEmpty) inProgress = false
              else executeNext()
          }
        }
        val promise = Promise[Unit]()
        queue.add(promise -> transition)
        if (!inProgress) {
          inProgress = true
          executeNext()
        }
        promise.future
      }

      def state = _state

      def subscribe[U](f: State => U): Dux.Unsubscribe = {
        subscribers = f :: subscribers
        () => subscribers = subscribers.filter(_ != f)
      }
    }
  }
}
