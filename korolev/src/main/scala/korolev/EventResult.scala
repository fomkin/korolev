package korolev

import korolev.Dux.Transition

import scala.concurrent.Future

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
case class EventResult[S](
    _immediateTransition: Option[Dux.Transition[S]] = None,
    _deferredTransition: Option[Future[Dux.Transition[S]]] = None,
    _stopPropagation: Boolean = false
) {

  def deferredTransition(transition: Future[Dux.Transition[S]]) =
    copy(_deferredTransition = Some(transition))

  def immediateTransition(transition: Dux.Transition[S]) =
    copy(_immediateTransition = Some(transition))

  def stopPropagation = copy(_stopPropagation = true)
}

object EventResult {
  def immediateTransition[S](transition: Dux.Transition[S]) =
    EventResult(Some(transition), None, _stopPropagation = false)

  def deferredTransition[S](transition: Future[Dux.Transition[S]]) =
    EventResult(None, Some(transition), _stopPropagation = false)

  /**
    * This is an immediateTransition return same state
    */
  def noTransition[S]: EventResult[S] = immediateTransition {
    case anyState => anyState
  }

  def transition[S](t: Transition[S]): Transition[S] = t
}
