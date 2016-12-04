package korolev

import java.util.concurrent.ConcurrentLinkedQueue

import scala.concurrent.{ExecutionContext, Future, Promise}

trait Dux[State] {
  def state: State
  def subscribe[U](f: State => U): Dux.Unsubscribe
  def onDestroy[U](f: () => U): Dux.Unsubscribe
  def destroy(): Unit
  def apply(action: Dux.Transition[State]): Future[Unit]
}

object Dux {

  type Unsubscribe = () => Unit
  type Transition[State] = PartialFunction[State, State]

  def apply[State](initialState: State)(implicit ec: ExecutionContext): Dux[State] = {

    new Dux[State] {

      val queue = new ConcurrentLinkedQueue[(Promise[Unit], Transition[State])]

      @volatile var _state = initialState
      @volatile var subscribers = List.empty[State => _]
      @volatile var onDestroyListeners = List.empty[() => _]
      @volatile var inProgress = false

      def apply(transition: Transition[State]): Future[Unit] = {
        def executeNext(): Unit = {
          val (promise, transition) = queue.poll()
          try {
            transition.lift(_state) match {
              case Some(newState) =>
                _state = newState
                subscribers.foreach(f => f(newState))
                promise.success(())
              case None =>
                promise.failure(new Exception("Transition don't fit this state"))
            }
          } catch {
            case e: Throwable => promise.failure(e)
          } finally {
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

      def destroy(): Unit = {
        for (listener <- onDestroyListeners)
          listener()
      }

      def state = _state

      def onDestroy[U](f: () => U): Unsubscribe = {
        onDestroyListeners = f :: onDestroyListeners
        () => onDestroyListeners = onDestroyListeners.filter(_ != f)
      }

      def subscribe[U](f: State => U): Dux.Unsubscribe = {
        subscribers = f :: subscribers
        () => subscribers = subscribers.filter(_ != f)
      }
    }
  }
}
