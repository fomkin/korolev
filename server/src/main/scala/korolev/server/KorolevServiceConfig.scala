package korolev.server

import korolev.server.KorolevServiceConfig.{ApplyTransition, Env, EnvConfigurator}
import korolev.server.StateStorage.{DeviceId, SessionId}
import korolev.{Async, Effects}
import levsha.{RenderContext, RenderUnit}

import scala.language.higherKinds

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
case class KorolevServiceConfig[F[+_]: Async, S, M](
  stateStorage: StateStorage[F, S],
  serverRouter: ServerRouter[F, S],
  render: RenderContext[Effects.Effect[F, S, M]] => PartialFunction[S, RenderUnit],
  head: RenderContext[Effects.Effect[F, S, M]] => Seq[RenderUnit.Node] = (rc: RenderContext[Effects.Effect[F, S, M]]) => Nil,
  maxFormDataEntrySize: Int = 1024 * 1024 * 8,
  envConfigurator: EnvConfigurator[F, S, M] =
    (_: DeviceId, _: SessionId, _: ApplyTransition[F, S]) =>
      Env(onDestroy = () => (), PartialFunction.empty)
)

object KorolevServiceConfig {
  case class Env[M](onDestroy: () => Unit, onMessage: PartialFunction[M, Unit])
  type ApplyTransition[F[+_], S] = PartialFunction[S, S] => F[Unit]
  type EnvConfigurator[F[+_], S, M] = (DeviceId, SessionId, ApplyTransition[F, S]) => Env[M]
}
