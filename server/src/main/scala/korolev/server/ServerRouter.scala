package korolev.server

import korolev.server.StateStorage.{DeviceId, SessionId}
import korolev.{Async, Router}

import scala.language.higherKinds

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
case class ServerRouter[F[+_]: Async, S](
  static: DeviceId => Router[F, S, Unit],
  dynamic: (DeviceId, SessionId) => Router[F, S, S],
  rootPath: String = "/") {

  def withRootPath(value: String): ServerRouter[F, S] = {
    copy(rootPath = value)
  }

  def withStatic(f: DeviceId => Router[F, S, Unit]): ServerRouter[F, S] =
    copy(static = f)

  def withDynamic(f: (DeviceId, SessionId) => Router[F, S, S]): ServerRouter[F, S] =
    copy(dynamic = f)
}

object ServerRouter {

  def empty[F[+_]: Async, S]: ServerRouter[F, S] =
    ServerRouter(emptyStatic[F, S], emptyDynamic[F, S])

  def apply[F[+_]: Async, S]: ServerRouter[F, S] =
    ServerRouter(emptyStatic[F, S], emptyDynamic[F, S])

  def emptyStatic[F[+_]: Async, S]: (DeviceId) => Router[F, S, Unit] =
    _ => Router.empty[F, S, Unit]

  def emptyDynamic[F[+_]: Async, S]: (DeviceId, SessionId) => Router[F, S, S] =
    (_, _) => Router.empty[F, S, S]
}


