/*
 * Copyright 2017-2018 Aleksey Fomkin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package korolev.state

import korolev.state.EnvConfigurator.Env
import korolev.{Async, Context}

trait EnvConfigurator[F[_], S, M] {

  def configure(access: Context.BaseAccess[F, S, M])
               (implicit F: Async[F]): F[Env[F, M]]

}

object EnvConfigurator {

  case class Env[F[_], M](onDestroy: () => F[Unit],
                           onMessage: PartialFunction[M, F[Unit]] = PartialFunction.empty)

  def default[F[_], S, M]: EnvConfigurator[F, S, M] =
    new DefaultEnvConfigurator

  def apply[F[_], S, M](f: Context.BaseAccess[F, S, M] => F[Env[F, M]]): EnvConfigurator[F, S, M] =
    new EnvConfigurator[F, S, M] {
      override def configure(access: Context.BaseAccess[F, S, M])
                            (implicit F: Async[F]): F[Env[F, M]] =
        f(access)
    }

  private class DefaultEnvConfigurator[F[_], S, M] extends EnvConfigurator[F, S, M] {
    override def configure(access: Context.BaseAccess[F, S, M])
                          (implicit F: Async[F]): F[Env[F, M]] =
      Async[F].delay(Env(onDestroy = () => Async[F].unit, PartialFunction.empty))
  }

}
