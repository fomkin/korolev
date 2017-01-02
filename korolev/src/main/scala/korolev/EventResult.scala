package korolev

import scala.language.higherKinds

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
case class EventResult[F[+_]: Async, S](
    it: Option[Dux.Transition[S]] = None,
    dt: Option[F[Dux.Transition[S]]] = None,
    sp: Boolean = false
) {

  def deferredTransition(transition: F[Dux.Transition[S]]): EventResult[F, S] =
    copy(dt = Some(transition))

  def immediateTransition(transition: Dux.Transition[S]): EventResult[F, S] =
    copy(it = Some(transition))

  def stopPropagation: EventResult[F, S] = copy(sp = true)
}
