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

/**
  * @param pull Function which should be invoked recursively until it return None.
  * @param finished Completes when all bytes was pulled
  * @param cancel Cancels pulling. After that pull can return None or Exception depends on implementation.
  * @param size known (or not) size
  */
final case class LazyBytes[F[_]](
    pull: () => F[Option[Array[Byte]]],
    finished: F[Unit],
    cancel: () => F[Unit],
    size: Option[Long])(implicit async: Async[F]) {

  /**
    * Folds all data to one byte array. Completes [[finished]].
    */
  def toStrict: F[Array[Byte]] = {
    def aux(acc: List[Array[Byte]]): F[List[Array[Byte]]] = {
      async.flatMap(pull()) {
        case Some(bytes) => aux(bytes :: acc)
        case None => async.delay(acc)
      }
    }
    async.map(aux(Nil)) { xs =>
      val length = xs.foldLeft(0)(_ + _.length)
      xs.foldRight(ByteBuffer.allocate(length))((a, b) => b.put(a)).array()
    }
  }

  /**
    * Same as [[toStrict]] but interprets bytes as UTF8 string.
    */
  def toStrictUtf8: F[String] = {
    async.map(toStrict)(bs => new String(bs, StandardCharsets.UTF_8))
  }

  /**
    * Drops all data. Completes [[finished]].
    */
  def discard(): F[Unit] = {
    def aux(): F[Unit] = async.flatMap(pull()) { x =>
      if (x.isEmpty) async.unit
      else aux()
    }
    aux()
  }
}

object LazyBytes {
  def empty[F[_]](implicit async: Async[F]): LazyBytes[F] = {
    val it = async.delay(Option.empty[Array[Byte]])
    LazyBytes(() => it, async.unit, () => async.unit, Some(0L))
  }
}
