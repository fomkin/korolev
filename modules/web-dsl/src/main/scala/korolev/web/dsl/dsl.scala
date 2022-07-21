package korolev.web

import korolev.web.Response.Status
import korolev.effect.Effect
import korolev.effect.syntax._

package object dsl {

  def response[F[_]: Effect, A, B](source: A, status: Response.Status = Status.Ok, headers: Map[String, String] = Map.empty)(
      implicit bf: BodyFactory[F, A, B]): F[Response[B]] = bf.mkBody(source).map { body =>
    val allHeaders = body.headers ++ headers
    Response(status, body.content, allHeaders.toSeq, body.contentLength)
  }

  def request[F[_]: Effect, B](method: Request.Method, pq: PathAndQuery)(
    implicit bf: EmptyBodyFactory[B]): F[Request[B]] = Effect[F].pure {
    val body = bf.emptyBody
    Request(method, pq, body.headers.toSeq, body.contentLength, body.content)
  }

  def request[F[_]: Effect, A, B](method: Request.Method, pq: PathAndQuery, source: A)(
    implicit bf: BodyFactory[F, A, B]): F[Request[B]] = bf.mkBody(source).map { body =>
    Request(method, pq, body.headers.toSeq, body.contentLength, body.content)
  }

  def request[F[_]: Effect, A, B](method: Request.Method, pq: PathAndQuery, source: A, headers: Map[String, String])(
    implicit bf: BodyFactory[F, A, B]): F[Request[B]] = bf.mkBody(source).map { body =>
    val allHeaders = body.headers ++ headers
    Request(method, pq, allHeaders.toSeq, body.contentLength, body.content)
  }

  // Routing

  final val / = PathAndQuery./
  type / = PathAndQuery./

  final val :& = PathAndQuery.:&
  type :& = PathAndQuery.:&

  final val :? = PathAndQuery.:?
  type :? = PathAndQuery.:?

  final val :?* = PathAndQuery.:?*
  final val *& = PathAndQuery.*&
  final val Root = PathAndQuery.Root

  object -> {
    def unapply[Body](request: Request[Body]): Option[(Request.Method, PathAndQuery)] =
      Some(request.method, request.pq)
  }
}
