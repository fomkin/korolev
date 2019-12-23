package korolev.server.internal.services

import korolev.effect.Effect
import korolev.effect.io.LazyBytes
import korolev.server.Response

private[korolev] final class CommonService[F[_]: Effect] {

  val notFoundResponse: Response.Http[F] =
    Response.Http(Response.Status.NotFound, "Not found", Nil)

  val notFoundResponseF: F[Response.Http[F]] =
    Effect[F].pure(notFoundResponse)

  val simpleOkResponse: Response.Http[F] = Response(
    status = Response.Status.Ok,
    body = LazyBytes.empty[F],
    headers = Nil
  )

}
