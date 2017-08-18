package korolev

case class EventResult[F[+_]: Async, S](
    it: Option[Transition[S]] = None,
    dt: Option[F[Transition[S]]] = None,
    sp: Boolean = false
) {

  def deferredTransition(transition: F[Transition[S]]): EventResult[F, S] =
    copy(dt = Some(transition))

  def immediateTransition(transition: Transition[S]): EventResult[F, S] =
    copy(it = Some(transition))

  def stopPropagation: EventResult[F, S] = copy(sp = true)
}
