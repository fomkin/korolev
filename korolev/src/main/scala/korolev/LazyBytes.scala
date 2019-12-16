/*
 * Copyright 2017-2018 Aleksey Fomkin
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

package korolev

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import korolev.effect.{Effect, Stream}

final case class LazyBytes[F[_]: Effect](chunks: Stream[F, Array[Byte]],
                                         bytesLength: Option[Long]) {

  /**
    * Folds all data to one byte array. Completes [[finished]].
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
    * Drops all data. Completes [[finished]].
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

  def apply[F[_]: Effect](bytes: Array[Byte]): LazyBytes[F] = {
    new LazyBytes(Stream.eval(bytes), Some(bytes.length))
  }

  def empty[F[_]: Effect]: LazyBytes[F] = {
    new LazyBytes[F](Stream.empty, Some(0L))
  }
}
