package korolev.server

import korolev.{Async, FormData}

import scala.util.Try

private[server] abstract class KorolevSession[F[+_]: Async] {
  def publish(message: String): F[Unit]
  def nextMessage: F[String]
  def destroy(): F[Unit]
  def resolveFormData(descriptor: String, formData: Try[FormData]): Unit
}
