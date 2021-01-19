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

import korolev.Qsid
import korolev.data.Bytes
import korolev.effect.syntax._
import korolev.effect.{Effect, Reporter, Stream}
import korolev.server.HttpResponse
import korolev.server.internal.FormDataCodec

import scala.concurrent.ExecutionContext

private[korolev] final class PostService[F[_]: Effect](reporter: Reporter,
                                                       sessionsService: SessionsService[F, _, _],
                                                       commonService: CommonService[F],
                                                       formDataCodec: FormDataCodec)
                                                      (implicit ec: ExecutionContext) {

  def formData(qsid: Qsid, descriptor: String, headers: Seq[(String, String)], data: Stream[F, Bytes]): F[HttpResponse[F]] = {

    def extractBoundary() = headers
      .collectFirst { case (k, v) if k.toLowerCase == "content-type" && v.contains("multipart/form-data") => v }
      .flatMap {
        _.split(';')
          .toList
          .filter(_.contains('='))
          .map(_.split('=').map(_.trim))
          .collectFirst { case Array("boundary", s) => s }
      }
      .fold(Effect[F].fail[String](new Exception("Content-Type should be `multipart/form-data`")))(Effect[F].pure)

    def parseFormData(formBytes: Bytes, boundary: String) = Effect[F].fork {
      Effect[F].delay {
        try {
          Right(formDataCodec.decode(formBytes.asBuffer, boundary))
        } catch {
          case error: Throwable =>
            Left(error)
        }
      }
    }

    sessionsService.getApp(qsid) flatMap {
      case Some(app) =>
        for {
          formBytes <- data.fold(Bytes.empty)(_ ++ _)
          boundary <- extractBoundary()
          errorOrFormData <- parseFormData(formBytes, boundary)
          _ <- app.frontend.resolveFormData(descriptor, errorOrFormData)
        } yield {
          commonService.simpleOkResponse
        }
      case None =>
        commonService.badRequest(ErrorSessionNotFound(qsid))
    }
  }

  def filesInfo(qsid: Qsid, descriptor: String, body: Stream[F, Bytes]): F[HttpResponse[F]] = {
    def parseFilesInfo(message: String) = message
      .split("\n")
      .toList
      .map { entry =>
        val slash = entry.lastIndexOf('/')
        (entry.substring(0, slash), entry.substring(slash + 1).toLong)
      }

    sessionsService.getApp(qsid) flatMap {
      case Some(app) =>
        for {
          message <- body.fold(Bytes.empty)(_ ++ _).map(_.asUtf8String) // file_name/size_in_bytes\n
          sizes = {
            if (message.isEmpty) List.empty[(String, Long)]
            else parseFilesInfo(message)
          }
          _ <- app.frontend.resolveFileNames(descriptor, sizes)
        } yield commonService.simpleOkResponse
      case None =>
        commonService.badRequest(ErrorSessionNotFound(qsid))
    }
  }

  def file(qsid: Qsid, descriptor: String, headers: Seq[(String, String)], body: Stream[F, Bytes]): F[HttpResponse[F]] = {
    val (consumed, chunks) = body.handleConsumed
    sessionsService.getApp(qsid) flatMap {
      case Some(app) =>
        for {
          _ <- app.frontend.resolveFile(descriptor, chunks)
          // Do not response until chunks are not
          // consumed inside an application
          _ <- consumed
        } yield commonService.simpleOkResponse
      case None =>
        commonService.badRequest(ErrorSessionNotFound(qsid))
    }
  }

  private def ErrorSessionNotFound(qsid: Qsid) =
    s"There is no app instance matched to $qsid"
}