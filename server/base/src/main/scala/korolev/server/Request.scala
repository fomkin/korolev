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
import korolev.Router
import korolev.effect.Stream
import korolev.effect.io.LazyBytes

final case class Request[Body](path: Router.Path,
                               param: String => Option[String],
                               cookie: String => Option[String],
                               headers: Seq[(String, String)],
                               body: Body)
    extends Request.RequestHeader

object Request {

  type Http[F[_]] = Request[LazyBytes[F]]
  type WebSocket[F[_]] = Request[Stream[F, String]]

  sealed trait RequestHeader {
    def path: Router.Path
    def param: String => Option[String]
    def cookie: String => Option[String]
    def headers: Seq[(String, String)]
  }

  sealed trait Method

  object Method {
    case object Post extends Method
    case object Get extends Method
    case object Put extends Method
    case object Delete extends Method
    case object Options extends Method
    case object Head extends Method
    case object Trace extends Method
    case object Connect extends Method
  }
}
