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

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.{Charset, StandardCharsets}

import korolev.effect.{Effect, Stream}
import korolev.effect.syntax._

import scala.annotation.tailrec

final case class LazyBytes[F[_] : Effect](chunks: Stream[F, Array[Byte]],
                                          bytesLength: Option[Long]) { lhs =>

  def ++(rhs: LazyBytes[F]): LazyBytes[F] =
    new LazyBytes(lhs.chunks ++ rhs.chunks,
      for (ls <- lhs.bytesLength; rs <- rhs.bytesLength) yield ls + rs)

  /**
    * Fold all data to one byte array
    */
  def toStrict: F[Array[Byte]] = {
    def aux(acc: List[Array[Byte]]): F[List[Array[Byte]]] = {
      Effect[F].flatMap(chunks.pull()) {
        case Some(bytes) => aux(bytes :: acc)
        case None => Effect[F].delay(acc)
      }
    }

    Effect[F].map(aux(Nil)) { xs =>
      val length = xs.foldLeft(0)(_ + _.length)
      xs.foldRight(ByteBuffer.allocate(length))((a, b) => b.put(a)).array()
    }
  }

  /**
    * Same as [[toStrict]] but interprets bytes as UTF8 string.
    */
  def toStrictUtf8: F[String] = {
    Effect[F].map(toStrict)(bs => new String(bs, StandardCharsets.UTF_8))
  }

  /**
    * Drop all data.
    */
  def discard(): F[Unit] = {
    def aux(): F[Unit] = Effect[F].flatMap(chunks.pull()) { x =>
      if (x.isEmpty) Effect[F].unit
      else aux()
    }

    aux()
  }
}

object LazyBytes {

  def apply[F[_] : Effect](s: String): F[LazyBytes[F]] =
    LazyBytes(s.getBytes(StandardCharsets.UTF_8))

  def apply[F[_] : Effect](s: String, charset: Charset): F[LazyBytes[F]] =
    LazyBytes(s.getBytes(charset))

  def apply[F[_] : Effect](bytes: Array[Byte])(implicit dummyImplicit: DummyImplicit): F[LazyBytes[F]] = {
    Stream(bytes)
      .mat()
      .map(bs => new LazyBytes(bs, Some(bytes.length.toLong)))
  }

  def fromInputStream[F[_] : Effect](inputStream: InputStream, chunkSize: Int = 8192): F[LazyBytes[F]] = {
    @tailrec
    def readStream(chunk: Array[Byte], offset: Int, len: Int): (Unit, Option[Array[Byte]]) = {
      val read = inputStream.read(chunk, offset, len)

      if (read == len) {
        ((), Some(chunk))
      } else {
        readStream(chunk, offset + read, len - read)
      }
    }

    val total = inputStream.available().toLong
    val streamF = Stream.unfoldResource[F, InputStream, Unit, Array[Byte]](
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
    Effect[F].map(streamF)(LazyBytes(_, Some(total)))
  }

  def empty[F[_] : Effect]: LazyBytes[F] = {
    new LazyBytes[F](Stream.empty, Some(0L))
  }
}
