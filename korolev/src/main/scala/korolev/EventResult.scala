package korolev

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
}
