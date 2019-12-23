package korolev.effect.io

import java.io.{BufferedReader, FileInputStream, FileOutputStream, FileReader}
import java.nio.file.{Files, Path}

import korolev.effect.syntax._
import korolev.effect.{Effect, Stream}

object FileIO {

  def readBytes[F[_]: Effect](path: Path): F[LazyBytes[F]] = {
    val inputStream = new FileInputStream(path.toFile)
    LazyBytes(inputStream)
  }

  def readLines[F[_]: Effect](path: Path): F[Stream[F, String]] = {
    Stream.unfoldResource[F, BufferedReader, Unit, String](
      default = (),
      create = Effect[F].delay(new BufferedReader(new FileReader(path.toFile))),
      calcSize = _ => Effect[F].delay(Option(Files.size(path))),
      loop = (reader, _) => Effect[F].delay {
        ((), Option(reader.readLine()))
      }
    )
  }

  /**
    * {{{
    *   lazyBytes.chunks.to(File.write(path, append = true))
    * }}}
    */
  def write[F[_]: Effect](path: Path, append: Boolean = false): Stream[F, Array[Byte]] => F[Unit] = { stream =>
    val outputStream = new FileOutputStream(path.toFile, append)
    def aux(): F[Unit] = {
      Effect[F].flatMap(stream.pull()) {
        case Some(chunk) => Effect[F]
          .delay(outputStream.write(chunk))
          .flatMap(_ => aux())
          .recover {
            case error =>
              outputStream.close()
              throw error
          }
        case None =>
          Effect[F].delay(outputStream.close())
      }
    }
    aux()
  }
}
