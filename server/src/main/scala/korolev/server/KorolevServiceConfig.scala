package korolev.server

import korolev.server.KorolevServiceConfig.{ApplyTransition, Env, EnvConfigurator}
import korolev.server.StateStorage.{DeviceId, SessionId}
import korolev.{Async, Render, VDom}

import scala.language.higherKinds

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
case class KorolevServiceConfig[F[+_]: Async, S, M](
  stateStorage: StateStorage[F, S],
  serverRouter: ServerRouter[F, S],
  render: Render[S] = PartialFunction.empty,
  head: VDom.Node = VDom.Node("head", Nil, Nil, Nil),
  envConfigurator: EnvConfigurator[F, S, M] =
    (_: DeviceId, _: SessionId, _: ApplyTransition[F, S]) =>
      Env(onDestroy = () => (), PartialFunction.empty)
)

object KorolevServiceConfig {
  case class Env[M](onDestroy: () => Unit, onMessage: PartialFunction[M, Unit])
  type ApplyTransition[F[+_], S] = PartialFunction[S, S] => F[Unit]
  type EnvConfigurator[F[+_], S, M] = (DeviceId, SessionId, ApplyTransition[F, S]) => Env[M]
}
