package korolev

import korolev.Dux.Transition

import scala.language.higherKinds

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
case class EventResult[F[_]: Async, S](
    _immediateTransition: Option[Dux.Transition[S]] = None,
    _deferredTransition: Option[F[Dux.Transition[S]]] = None,
    _stopPropagation: Boolean = false
) {

  def deferredTransition(transition: F[Dux.Transition[S]]): EventResult[F, S] =
    copy(_deferredTransition = Some(transition))

  def immediateTransition(transition: Dux.Transition[S]): EventResult[F, S] =
    copy(_immediateTransition = Some(transition))

  def stopPropagation: EventResult[F, S] = copy(_stopPropagation = true)
}

object EventResult {

  def immediateTransition[F[_]: Async, S](transition: Dux.Transition[S]): EventResult[F, S] =
    EventResult[F, S](Some(transition), None, _stopPropagation = false)

  def deferredTransition[F[_]: Async, S](transition: F[Dux.Transition[S]]): EventResult[F, S] =
    EventResult[F, S](None, Some(transition), _stopPropagation = false)

  /**
    * This is an immediateTransition return same state
    */
  def noTransition[F[_]: Async, S]: EventResult[F, S] = immediateTransition {
    case anyState => anyState
  }

  def transition[S](t: Transition[S]): Transition[S] = t
}
