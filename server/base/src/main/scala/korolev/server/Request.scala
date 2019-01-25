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

package korolev.server

import java.nio.ByteBuffer

import korolev.{Async, Router}

/**
  * @param body Deliver bytes of request body. empty array means EOF
  */
final case class Request[F[_]](
  path: Router.Path,
  params: Map[String, String],
  cookie: String => Option[String],
  headers: Seq[(String, String)],
  body: () => F[Array[Byte]]
)(implicit async: Async[F]) {

  def strictBody(): F[Array[Byte]] = {
    def aux(acc: List[Array[Byte]], b: F[Array[Byte]]): F[List[Array[Byte]]] = {
      async.flatMap(b) { bytes =>
        if (bytes.isEmpty) async.pure(acc)
        else aux(bytes :: acc, body())
      }
    }
    async.map(aux(Nil, body())) { xs =>
      val length = xs.foldLeft(0)(_ + _.length)
      xs.foldRight(ByteBuffer.allocate(length))((a, b) => b.put(a)).array()
    }
  }
}
