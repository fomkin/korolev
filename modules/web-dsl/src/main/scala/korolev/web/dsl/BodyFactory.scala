package korolev.web.dsl

import korolev.data.BytesLike
import korolev.web.Headers
import korolev.effect.{Effect, Stream}
import korolev.data.syntax._
import korolev.effect.syntax._

trait BodyFactory[F[_], A, B] {
  def mkBody(source: A): F[BodyFactory.Body[B]]
}

trait EmptyBodyFactory[A] {
  def emptyBody: BodyFactory.Body[A]
}

object BodyFactory {

  final case class Body[B](content: B, headers: Map[String, String], contentLength: Option[Long])

  private final class StringBytesLikeBodyFactory[F[_]: Effect, B: BytesLike] extends BodyFactory[F, String, B] {
    def mkBody(source: String): F[Body[B]] = Effect[F].pure {
      val bytes = BytesLike[B].utf8(source)
      Body(
        content = bytes,
        headers = Map(Headers.ContentTypeTextUtf8),
        contentLength = Some(bytes.length)
      )
    }
  }

  private final class JsonBodyFactory[F[_]: Effect, J: JsonCodec, B](implicit bf: BodyFactory[F, String, B])
      extends BodyFactory[F, J, B] {

    def mkBody(source: J): F[Body[B]] =
      bf.mkBody(implicitly[JsonCodec[J]].encode(source)).map { body =>
        body.copy(headers = Map(Headers.ContentType -> "application/json; charset=utf-8"))
      }
  }

  private final class StreamedBodyFactory[F[_]: Effect, A, B](implicit bf: BodyFactory[F, A, B])
      extends BodyFactory[F, A, Stream[F, B]] {

    def mkBody(source: A): F[Body[Stream[F, B]]] = {
      bf.mkBody(source).flatMap { body =>
        Stream(body.content).mat().map { stream =>
          Body(
            content = stream,
            headers = body.headers,
            contentLength = body.contentLength
          )
        }
      }
    }
  }

  implicit def stringBytesLikeBodyFactory[F[_]: Effect, B: BytesLike]: BodyFactory[F, String, B] =
    new StringBytesLikeBodyFactory[F, B]()

  implicit def jsonBodyFactory[F[_]: Effect, J: JsonCodec, B](implicit bf: BodyFactory[F, String, B]): BodyFactory[F, J, B] =
    new JsonBodyFactory[F, J, B]()

  implicit def streamedBodyFactory[F[_]: Effect, A, B](implicit bf: BodyFactory[F, A, B]): BodyFactory[F, A, Stream[F, B]] =
    new StreamedBodyFactory[F, A, B]()

  implicit def streamedEmptyBodyFactory[F[_]: Effect, A]: EmptyBodyFactory[Stream[F, A]] = new EmptyBodyFactory[Stream[F, A]] {
    val emptyBody: Body[Stream[F, A]] =
      Body(Stream.empty, Map.empty, None)
  }
}
