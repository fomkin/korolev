package korolev.state

import korolev.state.EnvConfigurator.Env
import korolev.{Async, Transition}

trait EnvConfigurator[F[+_], S, M] {

  def configure(deviceId: DeviceId, sessionId: SessionId, applyTransition: Transition[S] => F[Unit])
               (implicit F: Async[F]): F[Env[F, M]]

}

object EnvConfigurator {

  case class Env[F[+_], M](onDestroy: () => F[Unit],
                           onMessage: PartialFunction[M, F[Unit]] = PartialFunction.empty)

  def default[F[+_], S, M]: EnvConfigurator[F, S, M] =
    new DefaultEnvConfigurator

  def apply[F[+_], S, M](f: (DeviceId, SessionId, Transition[S] => F[Unit]) => F[Env[F, M]]): EnvConfigurator[F, S, M] =
    new EnvConfigurator[F, S, M] {
      override def configure(deviceId: DeviceId,
                             sessionId: SessionId,
                             applyTransition: Transition[S] => F[Unit])
                            (implicit F: Async[F]): F[Env[F, M]] =
        f(deviceId, sessionId, applyTransition)
    }

  private class DefaultEnvConfigurator[F[+_], S, M] extends EnvConfigurator[F, S, M] {
    override def configure(deviceId: DeviceId,
                           sessionId: SessionId,
                           applyTransition: Transition[S] => F[Unit])
                          (implicit F: Async[F]): F[Env[F, M]] =
      Async[F].pure(Env(onDestroy = () => Async[F].unit, PartialFunction.empty))
  }

}
