package korolev

import korolev.Dux.{Transition, Unsubscribe}
import korolev.Event.{Bubbling, Phase}

import scala.concurrent.Future

trait KorolevAccess[State] {
  def event[Payload](`type`: String, phase: Phase = Bubbling)
    (onFile: Payload => EventResult[State])
    (payload: Payload): Event

  def id(): ElementAccessor
  def dux: Dux[State]
}

object KorolevAccess {

  /**
    * Stateless environment.
    * Event and property accessors have no effects.
    */
  def dummy[T] = new KorolevAccess[T] {
    def event[Payload](tpe: String, phse: Phase)
      (onFile: (Payload) => EventResult[T])
      (pl: Payload): Event = {
      new Event {
        def fire() = true
        def `type` = tpe
        def payload = pl
        def phase = phse
      }
    }
    def id(): ElementAccessor = new ElementAccessor {
      def get[X](name: Symbol): Future[X] =
        Future.failed(new NotImplementedError())
    }
    def dux = new Dux[T] {
      def destroy(): Unit = ()
      def onDestroy[U](f: () => U): Unsubscribe = () => ()
      def subscribe[U](f: (T) => U): Unsubscribe = () => ()

      def state: T = throw new NoSuchElementException()
      def apply(action: Transition[T]): Future[Unit] =
        Future.failed(new NotImplementedError())
    }
  }
}
