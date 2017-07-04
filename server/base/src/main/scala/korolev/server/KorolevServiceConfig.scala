package korolev.server

import korolev.server.KorolevServiceConfig.{ApplyTransition, Env, EnvConfigurator}
import korolev.server.StateStorage.{DeviceId, SessionId}
import korolev.{ApplicationContext, Async}
import levsha.{Document, TemplateDsl}

case class KorolevServiceConfig[F[+_]: Async, S, M](
  stateStorage: StateStorage[F, S],
  serverRouter: ServerRouter[F, S],
  render: PartialFunction[S, Document.Node[ApplicationContext.Effect[F, S, M]]],
  head: Seq[Document.Node[ApplicationContext.Effect[F, S, M]]] = Seq.empty,
  connectionLostWidget: Document.Node[ApplicationContext.Effect[F, S, M]] =
    KorolevServiceConfig.defaultConnectionLostWidget[ApplicationContext.Effect[F, S, M]],
  maxFormDataEntrySize: Int = 1024 * 1024 * 8,
  envConfigurator: EnvConfigurator[F, S, M] =
    (_: DeviceId, _: SessionId, _: ApplyTransition[F, S]) =>
      Env(onDestroy = () => (), PartialFunction.empty)
)

object KorolevServiceConfig {
  case class Env[M](onDestroy: () => Unit, onMessage: PartialFunction[M, Unit])
  type ApplyTransition[F[+_], S] = PartialFunction[S, S] => F[Unit]
  type EnvConfigurator[F[+_], S, M] = (DeviceId, SessionId, ApplyTransition[F, S]) => Env[M]

  def defaultConnectionLostWidget[MiscType] = {
    val dsl = new TemplateDsl[MiscType]()
    import dsl._
    'div('style /= "position: absolute; top: 0; left: 0; right: 0;" +
                   "background-color: yellow; border-bottom: 1px solid black; padding: 10px;",
      "Connection lost. Waiting to resume."
    )
  }
}
