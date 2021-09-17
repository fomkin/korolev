package korolev.effect

trait Close[F[_], -T] {
  def onClose(that: T): F[Unit]
  def close(that: T): F[Unit]
}

object Close {
  def apply[F[_], T](implicit ev: Close[F, T]): Close[F, T] =
    implicitly[Close[F, T]]
}
