package korolev.effect.io

import korolev.data.BytesLike
import korolev.effect.{Effect, Stream}

import java.io.InputStream
import scala.annotation.tailrec

object JavaIO {

  def fromInputStream[F[_] : Effect, B: BytesLike](inputStream: InputStream, chunkSize: Int = 8192 * 2): F[Stream[F, B]] = {
    @tailrec
    def readStream(chunk: Array[Byte], offset: Int, len: Int): (Unit, Option[B]) = {
      val read = inputStream.read(chunk, offset, len)
      if (read == len) {
        ((), Some(BytesLike[B].wrapArray(chunk)))
      } else {
        readStream(chunk, offset + read, len - read)
      }
    }

    Stream.unfoldResource[F, InputStream, Unit, B](
      default = (),
      create = Effect[F].pure(inputStream),
      loop = (inputStream, _) => Effect[F].delay {
        if (inputStream.available() > 0) {
          val len = Math.min(inputStream.available(), chunkSize)
          val chunk = new Array[Byte](len)
          readStream(chunk, 0, len)
        } else {
          ((), None)
        }
      }
    )
  }

}
