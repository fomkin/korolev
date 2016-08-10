package korolev

import scala.collection.immutable.Queue
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

trait Dux[+State, -Action] {
  def state: Future[State]
  def subscribe[U](f: State => U): Dux.Unsubscribe
  def dispatch(action: Action): Future[Unit]
}

object Dux {

  type Unsubscribe = () => Unit
  type Reducer[State, Action] = (State, Action) => State


  def apply[State, Action](
      initialState: State, reducer: Reducer[State, Action])(
      implicit ec: ExecutionContext): Dux[State, Action] = {

    new Dux[State, Action] {

      @volatile var _state = initialState
      @volatile var queue = Queue.empty[(Promise[Unit], Action)]
      @volatile var subscribers = List.empty[State => _]
      @volatile var inProgress = false

      def dispatch(action: Action): Future[Unit] = {
        def executeNext(): Unit = {
          val ((promise, action), queueTail) = queue.dequeue
          queue = queueTail
          Future {
            val t = System.currentTimeMillis()
            val res = reducer(_state, action)
            println("reduce: " + (System.currentTimeMillis() - t) / 1000d)
            res
          } onComplete {
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
        queue = queue.enqueue(promise -> action)
        if (!inProgress) {
          inProgress = true
          executeNext()
        }
        promise.future
      }

      def state = Future.successful(_state)

      def subscribe[U](f: State => U): Dux.Unsubscribe = {
        subscribers = f :: subscribers
        () => subscribers = subscribers.filter(_ != f)
      }
    }
  }
}
