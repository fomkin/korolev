/*
 * Copyright 2017-2020 Aleksey Fomkin
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

package korolev

import Extension.Handlers
import korolev.effect.Effect

trait Extension[F[_], S, M] {

  /**
   * Invokes then new sessions created
   * @return
   */
  def setup(access: Context.BaseAccess[F, S, M]): F[Handlers[F, S, M]]
}

object Extension {

  abstract class Handlers[F[_]: Effect, S, M] {

    /**
     * Invokes when state updated.
     * @return
     */
    def onState(state: S): F[Unit] = Effect[F].unit

    /**
     * Invokes when message published.
     * @see Context.BaseAccess.publish
     */
    def onMessage(message: M): F[Unit] = Effect[F].unit

    /**
     * Invokes when user closes tab.
     * @return
     */
    def onDestroy(): F[Unit] = Effect[F].unit
  }

  private final class HandlersImpl[F[_]: Effect, S, M](_onState: S => F[Unit],
                                                       _onMessage: M => F[Unit],
                                                       _onDestroy: () => F[Unit]) extends Handlers[F, S, M] {
    override def onState(state: S): F[Unit] = _onState(state)
    override def onMessage(message: M): F[Unit] = _onMessage(message)
    override def onDestroy(): F[Unit] = _onDestroy()
  }

  object Handlers {

    /**
     * @param onState Invokes when state updated.
     * @param onMessage Invokes when message published.
     * @param onDestroy Invokes when user closes tab.
     */
    def apply[F[_]: Effect, S, M](onState: S => F[Unit] = null,
                                  onMessage: M => F[Unit] = null,
                                  onDestroy: () => F[Unit] = null): Handlers[F, S, M] =
      new HandlersImpl(
        if (onState == null) _ => Effect[F].unit else onState,
        if (onMessage == null) _ => Effect[F].unit else onMessage,
        if (onDestroy == null) () => Effect[F].unit else onDestroy
      )
  }

  def apply[F[_], S, M](f: Context.BaseAccess[F, S, M] => F[Handlers[F, S, M]]): Extension[F, S, M] =
    (access: Context.BaseAccess[F, S, M]) => f(access)

  def pure[F[_]: Effect, S, M](f: Context.BaseAccess[F, S, M] => Handlers[F, S, M]): Extension[F, S, M] =
    (access: Context.BaseAccess[F, S, M]) => Effect[F].pure(f(access))
}
