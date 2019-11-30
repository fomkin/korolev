package korolev.effect.io

import korolev.data.ByteVector
import korolev.effect.Stream

trait DataSocket[F[_]] {
  def stream: Stream[F, ByteVector]
  def write(bytes: ByteVector): F[Unit]
}
