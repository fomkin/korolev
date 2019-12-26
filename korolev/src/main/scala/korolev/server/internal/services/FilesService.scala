package korolev.server.internal.services

import korolev.Router._
import korolev.effect.Effect
import korolev.effect.io.LazyBytes
import korolev.effect.syntax._

import korolev.server.{Headers, Response}
import korolev.server.internal.MimeTypes

private[korolev] final class FilesService[F[_]: Effect](commonService: CommonService[F]) {

  import commonService._

  def resourceFromClasspath(path: Path): F[Response.Http[F]] = {
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
      LazyBytes(javaSyncStream) map { lazyBytes =>
        Response(Response.Status.Ok, lazyBytes, headers)
      }
    }
  }
}
