package korolev.state

import korolev.{Async, Transition}

case class Env[F[+_], M](onDestroy: () => F[Unit],
                         onMessage: PartialFunction[M, Unit] = PartialFunction.empty)

trait EnvConfigurator[F[+_], S, M] {

  def configure(deviceId: DeviceId, sessionId: SessionId, applyTransition: Transition[S] => F[Unit])
               (implicit F: Async[F]): F[Env[F, M]]

}

object EnvConfigurator {

  def default[F[+_], S, M]: EnvConfigurator[F, S, M] =
    new DefaultEnvConfigurator

  private class DefaultEnvConfigurator[F[+_], S, M] extends EnvConfigurator[F, S, M] {
    override def configure(deviceId: DeviceId,
                           sessionId: SessionId,
                           applyTransition: Transition[S] => F[Unit])
                          (implicit F: Async[F]): F[Env[F, M]] =
      Async[F].pure(Env(onDestroy = () => Async[F].unit, PartialFunction.empty))
  }

}
