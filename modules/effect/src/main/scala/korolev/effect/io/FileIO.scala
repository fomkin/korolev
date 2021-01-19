/*
 * Copyright 2017-2020 Aleksey Fomkin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package korolev.effect.io

import korolev.data.BytesLike

import java.io.{BufferedReader, FileInputStream, FileOutputStream, FileReader}
import java.nio.file.Path
import korolev.effect.syntax._
import korolev.effect.{Effect, Stream}

object FileIO {

  def readBytes[F[_]: Effect, B: BytesLike](path: Path): F[Stream[F, B]] = {
    val inputStream = new FileInputStream(path.toFile)
    JavaIO.fromInputStream(inputStream)
  }

  def readLines[F[_]: Effect](path: Path): F[Stream[F, String]] = {
    Stream.unfoldResource[F, BufferedReader, Unit, String](
      default = (),
      create = Effect[F].delay(new BufferedReader(new FileReader(path.toFile))),
      loop = (reader, _) => Effect[F].delay {
        ((), Option(reader.readLine()))
      }
    )
  }

  /**
    * {{{
    *   chunks.to(File.write(path, append = true))
    * }}}
    */
  def write[F[_]: Effect, B: BytesLike](path: Path, append: Boolean = false): Stream[F, B] => F[Unit] = { stream =>
    val outputStream = new FileOutputStream(path.toFile, append)
    def aux(): F[Unit] = {
      stream.pull().flatMap {
        case Some(chunk) => Effect[F]
          .delay(outputStream.write(BytesLike[B].asArray(chunk)))
          .after(aux())
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
