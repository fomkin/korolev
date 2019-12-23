package korolev.server.internal.services

import java.nio.ByteBuffer

import korolev.effect.AsyncTable.AlreadyContainsKeyException
import korolev.effect.io.LazyBytes
import korolev.{Context, Qsid}
import korolev.effect.{AsyncTable, Effect, Reporter, Stream}
import korolev.server.Response
import korolev.effect.syntax._
import korolev.server.internal.{BadRequestException, FormDataCodec}

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
      _ <- app.frontend.resolveFormData(descriptor, tryFormData.toEither)
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
      files.get(FileId(qsid, descriptor, fileName, timestamp))

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
      _ <- app.frontend.resolveFiles(descriptor, files)
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
            files
              .put(id, body.chunks)
              .flatMap(_ => files.remove(id))
              .recover {
                case AlreadyContainsKeyException(_) =>
                  throw BadRequestException("This upload already started")
              }
        }
      }
      // Do not response until chunks are not
      // consumed inside an application
      _ <- body.chunks.consumed
    } yield commonService.simpleOkResponse

  private val files = AsyncTable.empty[F, FileId, Stream[F, Array[Byte]]]

  private case class FileId(qsid: Qsid,
                            descriptor: String,
                            fileName: String,
                            timestamp: Long = 0) { lhs =>
    override lazy val hashCode: Int =
      (qsid, descriptor, fileName).hashCode()

    override def equals(obj: Any): Boolean = obj match {
      case rhs: FileId =>
        lhs.qsid == rhs.qsid &&
          lhs.descriptor == rhs.descriptor &&
          lhs.fileName == rhs.fileName
      case _ => false
    }
  }
}
