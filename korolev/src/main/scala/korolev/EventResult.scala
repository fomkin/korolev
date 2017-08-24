package korolev

case class EventResult[F[+_]: Async, S](effect: F[Unit], stopPropagation: Boolean)
