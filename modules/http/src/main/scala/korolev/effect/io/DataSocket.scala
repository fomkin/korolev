package korolev.effect.io

import korolev.effect.Stream

trait DataSocket[F[_], B] {
  def stream: Stream[F, B]
  def write(bytes: B): F[Unit]
}
