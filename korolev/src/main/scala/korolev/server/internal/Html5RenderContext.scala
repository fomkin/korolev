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

package korolev.server.internal

import korolev.Context
import korolev.Context._
import korolev.effect.Effect
import levsha.RenderContext
import levsha.impl.TextPrettyPrintingConfig

private[korolev] final class Html5RenderContext[F[_]: Effect, S]
  extends levsha.impl.Html5RenderContext[Binding[F, S, _]](TextPrettyPrintingConfig.noPrettyPrinting) {

  override def addMisc(misc: Binding[F, S, _]): Unit = misc match {
    case ComponentEntry(component, parameters, _) =>
      val rc = this.asInstanceOf[RenderContext[Context.Binding[F, Any, Any]]]
      // Static pages always made from scratch
      component.render(parameters, component.initialState).apply(rc)
    case _ => ()
  }

}
