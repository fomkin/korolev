package korolev.internal

import bridge.JSObj
import korolev.Async
import levsha.Id

/**
  * Typed interface to client side
  */
final class Frontend[F[+ _]: Async](client: JSObj[F]) {

  def listenEvent(name: String, preventDefault: Boolean): F[Unit] =
    client.callAndFlush("ListenEvent", name, preventDefault)

  def uploadForm(id: Id, descriptor: String): F[Unit] =
    client.callAndFlush("UploadForm", id.mkString, descriptor)

  def focus(id: Id): F[Unit] =
    client.callAndFlush("Focus", id.mkString)

  def setAttr[T](id: Id, xmlNs: String, name: String, value: T, isProperty: Boolean): F[Unit] =
    client.callAndFlush("SetAttr", id.mkString, xmlNs, name, value, isProperty)

  def extractProperty[T](id: Id, name: String): F[T] =
    client.callAndFlush("ExtractProperty", id.mkString, name)
}
