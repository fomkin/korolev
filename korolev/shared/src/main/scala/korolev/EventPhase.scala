package korolev

sealed trait EventPhase

object EventPhase {
  case object Capturing extends EventPhase
  case object AtTarget extends EventPhase
  case object Bubbling extends EventPhase
}

