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

package korolev.server.internal.services

import korolev.data.Bytes
import korolev.effect.Effect
import korolev.effect.io.JavaIO
import korolev.effect.syntax._
import korolev.server.HttpResponse
import korolev.server.internal.MimeTypes
import korolev.web.PathAndQuery._
import korolev.web.{Headers, PathAndQuery, Response}

private[korolev] final class FilesService[F[_]: Effect](commonService: CommonService[F]) {

  import commonService._

  def resourceFromClasspath(path: PathAndQuery): F[HttpResponse[F]] = {
    val fsPath = path.mkString
    val maybeResourceStream = Option(this.getClass.getResourceAsStream(fsPath))
    maybeResourceStream.fold(notFoundResponseF) { javaSyncStream =>
      val _ / fileName = path
      val fileExtension = fileName.lastIndexOf('.') match {
        case -1    => "bin" // default file extension
        case index => fileName.substring(index + 1)
      }
      val headers = MimeTypes(fileExtension) match {
        case Some(mimeType) => Seq(Headers.ContentType -> mimeType)
        case None           => Nil
      }
      JavaIO.fromInputStream[F, Bytes](javaSyncStream) map { stream =>
        Response(Response.Status.Ok, stream, headers, Some(javaSyncStream.available().toLong))
      }
    }
  }
}
