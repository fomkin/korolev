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

package korolev.server

import korolev.effect.{Effect, Reporter}
import korolev.state.IdGenerator
import korolev.{Context, Extension, Router}
import levsha.Document

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

case class KorolevServiceConfig[F[_]: Effect, S, M](
  stateLoader: StateLoader[F, S],
  stateStorage: korolev.state.StateStorage[F, S] = null, // By default it StateStorage.DefaultStateStorage
  router: Router[F, S] = Router.empty[F, S],
  rootPath: String = "/",
  render: S => Document.Node[Context.Binding[F, S, M]] = (_: S) => levsha.dsl.html.body(),
  head: S => Seq[Document.Node[Context.Binding[F, S, M]]] = (_: S) => Seq.empty,
  connectionLostWidget: Document.Node[Context.Binding[F, S, M]] =
    KorolevServiceConfig.defaultConnectionLostWidget[Context.Binding[F, S, M]],
  maxFormDataEntrySize: Int = 1024 * 8,
  extensions: List[Extension[F, S, M]] = Nil,
  idGenerator: IdGenerator[F] = IdGenerator.default[F](),
  heartbeatInterval: FiniteDuration = 5.seconds,
  reporter: Reporter = Reporter.PrintReporter
)(implicit val executionContext: ExecutionContext)

object KorolevServiceConfig {

  def defaultConnectionLostWidget[MiscType]: Document.Node[MiscType] = {
    import levsha.dsl._
    import html._
    optimize {
      div(
        position @= "fixed",
        top @= "0",
        left @= "0",
        right @= "0",
        backgroundColor @= "lightyellow",
        borderBottom @= "1px solid black",
        padding @= "10px",
        "Connection lost. Waiting to resume."
      )
    }
  }
}
