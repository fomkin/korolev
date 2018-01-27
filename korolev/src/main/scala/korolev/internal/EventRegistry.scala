package korolev.internal

import korolev.Async

import scala.collection.mutable

/**
  * Save information about what type of events are already
  * listening on the client
  */
final class EventRegistry[F[+ _]: Async](frontend: ClientSideApi[F]) {

  private val knownEventTypes = mutable.Set('submit)

  /**
    * Notifies client side that he should listen
    * all events of the type. If event already listening
    * on the client side, client will be not notified again.
    */
  def registerEventType(`type`: Symbol, preventDefault: Boolean): Unit = knownEventTypes.synchronized {
    if (!knownEventTypes.contains(`type`)) {
      knownEventTypes += `type`
      frontend.listenEvent(`type`.name, preventDefault)
    }
  }
}
