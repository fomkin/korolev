package korolev.server

import korolev.data.Bytes

import java.nio.charset.StandardCharsets
import korolev.effect.{Effect, Stream}
import korolev.effect.syntax._

import korolev.web.Response
import korolev.web.Response.Status

package object internal {

  def HttpResponse[F[_]: Effect](status: Status): HttpResponse[F] = {
    new Response(status, Stream.empty, Nil, Some(0L))
  }

  def HttpResponse[F[_]: Effect](status: Status,
                                 body: Array[Byte], headers: Seq[(String, String)]): F[HttpResponse[F]] =
    Stream(Bytes.wrap(body))
      .mat[F]()
      .map(lb => new Response(status, lb, headers, Some(body.length.toLong)))

  def HttpResponse[F[_]: Effect](status: Status, message: String, headers: Seq[(String, String)]): F[HttpResponse[F]] =
    HttpResponse(status, message.getBytes(StandardCharsets.UTF_8), headers)
}
