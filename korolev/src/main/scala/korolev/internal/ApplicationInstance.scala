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

package korolev.internal

import java.util.concurrent.atomic.AtomicInteger

import korolev.Context._
import korolev.Async.AsyncOps
import korolev.execution.Scheduler
import korolev._
import korolev.state.{StateDeserializer, StateManager, StateSerializer}
import levsha.events.calculateEventPropagation
import levsha.impl.DiffRenderContext
import levsha.{Document, Id, XmlNs}

final class ApplicationInstance
  [
    F[_]: Async: Scheduler,
    S: StateSerializer: StateDeserializer,
    M
  ](
    sessionId: QualifiedSessionId,
    connection: Connection[F],
    stateManager: StateManager[F, S],
    initialState: S,
    render: PartialFunction[S, Document.Node[Effect[F, S, M]]],
    router: Router[F, S],
    fromScratch: Boolean,
    reporter: Reporter
  ) {

  import reporter.Implicit

  private val devMode = new DevMode.ForRenderContext(sessionId.toString, fromScratch)
  private val currentRenderNum = new AtomicInteger(0)
  private val renderContext = DiffRenderContext[Effect[F, S, M]](savedBuffer = devMode.loadRenderContext())
  private val frontend = new ClientSideApi[F](connection, reporter)

  val topLevelComponentInstance: ComponentInstance[F, S, M, S, Any, M] = {
    val renderer = render.lift
    val eventRegistry = new EventRegistry[F](frontend)
    val component = new Component[F, S, Any, M](initialState, Component.TopLevelComponentId) {
      def render(parameters: Any, state: S): Document.Node[Effect[F, S, M]] = {
        renderer(state).getOrElse {
          reporter.error(s"Render is not defined for $state")
          Document.Node[Effect[F, S, M]] { rc =>
            rc.openNode(XmlNs.html, "body")
            rc.addTextNode("Render is not defined for the state")
            rc.closeNode("body")
          }
        }
      }
    }
    new ComponentInstance[F, S, M, S, Any, M](
      Id.TopLevel, sessionId, frontend, eventRegistry,
      stateManager, () => currentRenderNum.get(), component, reporter
    )
  }

  private def onState(): F[Unit] = stateManager.snapshot.map { snapshot =>
    renderContext.synchronized {
      // Set page url if router exists
      router.fromState
        .lift(snapshot(Id.TopLevel).getOrElse(initialState))
        .foreach(path => frontend.changePageUrl(path))

      // Prepare render context
      renderContext.swap()

      // Perform rendering
      topLevelComponentInstance.applyRenderContext(
        parameters = (), // Boxed unit as parameter. Top level component doesn't need parameters
        snapshot = snapshot,
        rc = renderContext
      )

      // Infer changes
      frontend.startDomChanges()
      renderContext.diff(frontend)
      frontend.flushDomChanges()

      if (devMode.isActive)
        devMode.saveRenderContext(renderContext)

      // Make korolev ready to next render
      topLevelComponentInstance.dropObsoleteMisc()
      frontend.setRenderNum(currentRenderNum.incrementAndGet())
    }
  }

  frontend.setHandlers(
    onHistory = { path =>
      stateManager
        .read[S](Id.TopLevel)
        .flatMap { maybeTopLevelState =>
          router
            .toState
            .lift((maybeTopLevelState.getOrElse(initialState), path))
            .fold(Async[F].pure(Option.empty[S]))(_.map(Some(_)))
        }
        .flatMap {
          case Some(newState) =>
            stateManager.write(Id.TopLevel, newState)
              .flatMap(_ => onState())
          case None =>
            Async[F].unit
        }
        .runIgnoreResult
    },
    onEvent = { (renderNum, target, tpe) =>
      if (currentRenderNum.get == renderNum) {
        calculateEventPropagation(target, tpe) forall { eventId =>
          topLevelComponentInstance.applyEvent(eventId)
        }
        ()
      }
    },
    onFormDataProgress = topLevelComponentInstance.handleFormDataProgress
  )

  frontend.setRenderNum(0)

  stateManager.snapshot runOrReport { snapshot =>
    // Content should be created from scratch
    // Remove all element from document.body
    if (fromScratch)
      frontend.cleanRoot()

    // If dev mode is enabled and active
    // CSS should be reloaded
    if (devMode.isActive)
      frontend.reloadCss()

    // Perform initial rendering
    if (fromScratch) {
      renderContext.openNode(levsha.XmlNs.html, "body")
      renderContext.closeNode("body")
      renderContext.diff(DiffRenderContext.DummyChangesPerformer)
    } else {

      if (devMode.hasSavedRenderContext) {
        renderContext.swap()
        topLevelComponentInstance.applyRenderContext((), renderContext, snapshot)
        frontend.startDomChanges()
        renderContext.diff(frontend)
        frontend.flushDomChanges()
        devMode.saveRenderContext(renderContext)
      } else {

        topLevelComponentInstance.applyRenderContext((), renderContext, snapshot)
        renderContext.diff(DiffRenderContext.DummyChangesPerformer)

        if (devMode.isActive)
          devMode.saveRenderContext(renderContext)
      }
    }

    topLevelComponentInstance.subscribeStateChange { (_, _) =>
      onState().runIgnoreResult
    }

    if (fromScratch)
      onState().runIgnoreResult
  }
}
