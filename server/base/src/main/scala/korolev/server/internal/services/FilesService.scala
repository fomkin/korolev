package korolev.server.internal.services

import korolev.LazyBytes
import korolev.Router._
import korolev.effect.Effect
import korolev.server.{Headers, Response}
import korolev.server.internal.MimeTypes

private[korolev] final class FilesService[F[_]: Effect](commonService: CommonService[F]) {

  import commonService._

  def resourceFromClasspath(path: Path): F[Response.Http[F]] = Effect[F].delay {
    val fsPath = path.mkString
    val maybeResourceStream = Option(this.getClass.getResourceAsStream(fsPath))
    maybeResourceStream.fold(notFoundResponse) { javaSyncStream =>
      val _ / fileName = path
      val bytes = LazyBytes(javaSyncStream)
      val fileExtension = fileName.lastIndexOf('.') match {
        case -1    => "bin" // default file extension
        case index => fileName.substring(index + 1)
      }
      val headers = MimeTypes(fileExtension) match {
        case Some(mimeType) => Seq(Headers.ContentType -> mimeType)
        case None           => Nil
      }
      Response(Response.Status.Ok, bytes, headers)
    }
  }
}
