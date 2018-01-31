package korolev.internal

import korolev.Async
import korolev.Context.Event

import scala.collection.mutable

/**
  * Save information about what type of events are already
  * listening on the client
  */
final class EventRegistry[F[+ _]: Async](frontend: ClientSideApi[F]) {

  private val knownEventTypes = mutable.Set[(Symbol, Option[String])](('submit, None))

  /**
    * Notifies client side that he should listen
    * all events of the type. If event already listening
    * on the client side, client will be not notified again.
    */
  def registerEventType(`type`: Symbol, policy: Event.Policy): Unit = knownEventTypes.synchronized {
    if (!knownEventTypes.contains((`type`, policy.className))) {
      knownEventTypes += ((`type`, policy.className))
      frontend.listenEvent(`type`.name, policy)
    }
  }
}
