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
import korolev.effect.{AsyncTable, Effect, Stream}
import korolev.effect.io.JavaIO
import korolev.effect.syntax._
import korolev.server.HttpResponse
import korolev.web.PathAndQuery._
import korolev.web.{Headers, MimeTypes, Path, PathAndQuery, Response}

private[korolev] final class FilesService[F[_]: Effect](commonService: CommonService[F]) {

  type ResponseFactory = () => F[HttpResponse[F]]

  private val table = AsyncTable.unsafeCreateEmpty[F, Path, ResponseFactory]

  private val notFoundToken = Effect[F].pure(() => commonService.notFoundResponseF)

  def resourceFromClasspath(pq: PathAndQuery): F[HttpResponse[F]] = {
    val path = pq.asPath
    table
      .getFill(path) {
        val fsPath = path.mkString
        val maybeResourceStream = Option(this.getClass.getResourceAsStream(fsPath))
        maybeResourceStream.fold(notFoundToken) { javaSyncStream =>
          val _ / fileName = path
          val fileExtension = fileName.lastIndexOf('.') match {
            case -1    => "bin" // default file extension
            case index => fileName.substring(index + 1)
          }
          val headers = MimeTypes.typeForExtension.get(fileExtension) match {
            case Some(mimeType) => Seq(Headers.ContentType -> mimeType)
            case None           => Nil
          }
          val size = javaSyncStream.available().toLong
          for {
            stream <- JavaIO.fromInputStream[F, Bytes](javaSyncStream) // TODO configure chunk size
            chunks <- stream.fold(Vector.empty[Bytes])(_ :+ _)
            template = Stream.emits(chunks)
          } yield {
            () => template.mat() map { stream =>
              Response(Response.Status.Ok, stream, headers, Some(size))
            }
          }
        }
      }
      .flatMap(_())
  }
}
