package korolev.server.internal.services

import java.nio.ByteBuffer

import korolev.{Context, LazyBytes, Qsid}
import korolev.effect.{Effect, Reporter, Stream}
import korolev.server.Response
import korolev.effect.syntax._
import korolev.server.internal.{BadRequestException, FormDataCodec}

import scala.collection.mutable
import scala.util.Try

private[korolev] final class PostService[F[_]: Effect](reporter: Reporter,
                                                       sessionsService: SessionsService[F, _, _],
                                                       commonService: CommonService[F],
                                                       formDataCodec: FormDataCodec) {

  def formData(qsid: Qsid, descriptor: String, headers: Seq[(String, String)], data: LazyBytes[F]): F[Response.Http[F]] = {

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

    for {
      app <- sessionsService.findApp(qsid)
      formBytes <- data.toStrict
      boundary <- extractBoundary()
      tryFormData = Try(formDataCodec.decode(ByteBuffer.wrap(formBytes), boundary))
      _ <- Effect[F].delay(app.topLevelComponentInstance.resolveFormData(descriptor, tryFormData))
    } yield {
      commonService.simpleOkResponse
    }
  }

  def filesInfo(qsid: Qsid, descriptor: String, body: LazyBytes[F]): F[Response.Http[F]] = {

    def parseFilesInfo(message: String) = message
      .split("\n")
      .toList
      .map { entry =>
        val slash = entry.lastIndexOf('/')
        (entry.substring(0, slash), entry.substring(slash + 1).toLong)
      }

    def createFilePromise(fileName: String, timestamp: Long) =
      Effect[F].promise[Stream[F, Array[Byte]]] { cb =>
        val id = FileId(qsid, descriptor, fileName, timestamp)
        files.synchronized {
          files.get(id) match {
            case Some(Right(stream)) => cb(Right(stream))
            case _ => files.put(id, Left(cb))
          }
        }
      }

    for {
      app <- sessionsService.findApp(qsid)
      message <- body.toStrictUtf8 // file_name/size_in_bytes\n
      sizes =
        if (message.isEmpty) List.empty[(String, Long)]
        else parseFilesInfo(message)
      timestamp <- Effect[F].delay(System.currentTimeMillis())
      files = sizes.map {
        case (fileName, size) =>
          val promise = createFilePromise(fileName, timestamp)
          val bytes = LazyBytes(Stream.proxy(promise), Some(size))
          Context.File(fileName, bytes)
      }
      _ <- Effect[F].delay(app.topLevelComponentInstance.resolveFile(descriptor, files))
    } yield
      commonService.simpleOkResponse
  }

  def file(qsid: Qsid, descriptor: String, headers: Seq[(String, String)], body: LazyBytes[F]): F[Response.Http[F]] =
    for {
      _ <- Effect[F].delay {
        headers.collectFirst { case ("x-name", v) => v } match {
          case None => Effect[F].pure(Response.Http(Response.Status.BadRequest, "Header 'x-name' should be defined", Nil))
          case Some(fileName) =>
            val id = FileId(qsid, descriptor, fileName)
            files.synchronized {
              files.get(id) match {
                case Some(Left(cb)) =>
                  cb(Right(body.chunks))
                  files.remove(id)
                case None =>
                  files.put(id, Right(body.chunks))
                case Some(Right(_)) =>
                  throw BadRequestException("This upload already started")
              }
            }
        }
      }
    } yield commonService.simpleOkResponse

  private val files = mutable.Map.empty[
    FileId,
    Either[
      Effect.Promise[Stream[F, Array[Byte]]],
      Stream[F, Array[Byte]]
    ]
  ]

  private case class FileId(qsid: Qsid,
                            descriptor: String,
                            fileName: String,
                            timestamp: Long = 0) {
    override lazy val hashCode: Int =
      (qsid, descriptor, fileName).hashCode()
  }
}
