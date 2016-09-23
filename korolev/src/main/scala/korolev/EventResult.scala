package korolev

import scala.concurrent.Future

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
case class EventResult[+A](
    _immediateAction: Option[A] = None,
    _deferredAction: Option[Future[A]] = None,
    _stopPropagation: Boolean = false
) {

  def deferredAction[B >: A](action: Future[B]) =
    copy(_deferredAction = Some(action))
  def immediateAction[B >: A](action: B) =
    copy(_immediateAction = Some(action))

  def stopPropagation = copy(_stopPropagation = true)
}

object EventResult {
  def immediateAction[A](action: A) =
    EventResult(Some(action), None, _stopPropagation = false)
  def deferredAction[A](action: Future[A]) =
    EventResult(None, Some(action), _stopPropagation = false)
}
