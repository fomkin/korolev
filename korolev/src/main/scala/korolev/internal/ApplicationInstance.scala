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
import korolev.Router.Path
import korolev.effect.syntax._
import korolev._
import korolev.effect.{Effect, Stream, Hub, Queue, Reporter}
import korolev.internal.Frontend.DomEventMessage
import korolev.state.{StateDeserializer, StateManager, StateSerializer}
import levsha.events.calculateEventPropagation
import levsha.impl.DiffRenderContext
import levsha.impl.DiffRenderContext.ChangesPerformer
import levsha.{Document, Id, XmlNs}

final class ApplicationInstance
  [
    F[_]: Effect,
    S: StateSerializer: StateDeserializer,
    M
  ](
     sessionId: Qsid,
     val frontend: Frontend[F],
     stateManager: StateManager[F],
     initialState: S,
     render: S => Document.Node[Binding[F, S, M]],
     router: Router[F, S],
     scheduler: Scheduler[F],
     reporter: Reporter
  ) { application =>

  import reporter.Implicit

  private val devMode = new DevMode.ForRenderContext(sessionId.toString)
  private val currentRenderNum = new AtomicInteger(0)
  private val stateQueue = Queue[F, (Id, Any)]()
  private val messagesQueue = Queue[F, M]()

  private val renderContext = {
    DiffRenderContext[Binding[F, S, M]](savedBuffer = devMode.loadRenderContext())
  }

  val topLevelComponentInstance: ComponentInstance[F, S, M, S, Any, M] = {
    val eventRegistry = new EventRegistry[F](frontend)
    val component = new Component[F, S, Any, M](initialState, Component.TopLevelComponentId) {
      def render(parameters: Any, state: S): Document.Node[Binding[F, S, M]] = {
        try {
          application.render(state)
        } catch {
          case e: MatchError =>
            Document.Node[Binding[F, S, M]] { rc =>
              reporter.error(s"Render is not defined for $state")
              rc.openNode(XmlNs.html, "body")
              rc.addTextNode("Render is not defined for the state. ")
              rc.addTextNode(e.getMessage())
              rc.closeNode("body")
            }
        }
      }
    }
    val componentInstance = new ComponentInstance[F, S, M, S, Any, M](
      Id.TopLevel, sessionId, frontend, eventRegistry,
      stateManager, () => currentRenderNum.get(), component,
      notifyStateChange = (id, state) => onState() >> stateQueue.offer(id, state),
      scheduler, reporter
    )
    componentInstance.setEventsSubscription(messagesQueue.offerUnsafe)
    componentInstance
  }

  /**
    * If dev mode is enabled save render context
    */
  private def saveRenderContextIfNecessary(): F[Unit] =
    if (devMode.isActive) Effect[F].delay(devMode.saveRenderContext(renderContext))
    else Effect[F].unit

  private def nextRenderNum(): F[Int] =
    Effect[F].delay(currentRenderNum.incrementAndGet())

  private def onState(): F[Unit] =
    for {
      snapshot <- stateManager.snapshot
      // Set page url if router exists
      _ <-  router.fromState
          .lift(snapshot(Id.TopLevel).getOrElse(initialState))
          .fold(Effect[F].unit)(path => frontend.changePageUrl(path))
      _ <- Effect[F].delay {
        // Prepare render context
        renderContext.swap()
        // Perform rendering
        topLevelComponentInstance.applyRenderContext(
          parameters = (), // Boxed unit as parameter. Top level component doesn't need parameters
          snapshot = snapshot,
          rc = renderContext
        )
      }
      // Infer and perform changes
      _ <- frontend.performDomChanges(renderContext.diff)
      _ <- saveRenderContextIfNecessary()
      // Make korolev ready to next render
      renderNum <- nextRenderNum()
      _ <- Effect[F].delay(topLevelComponentInstance.dropObsoleteMisc())
      _ <- frontend.setRenderNum(renderNum)
    } yield ()


  private def onHistory(path: Path): F[Unit] =
    stateManager
      .read[S](Id.TopLevel)
      .flatMap { maybeTopLevelState =>
        router
          .toState
          .lift(path)
          .fold(Effect[F].delay(Option.empty[S]))(_(maybeTopLevelState.getOrElse(initialState)).map(Some(_)))
      }
      .flatMap {
        case Some(newState) =>
          stateManager
            .write(Id.TopLevel, newState)
            .after(onState())
        case None =>
          Effect[F].unit
      }

  private def onEvent(dem: DomEventMessage): F[Unit] =
    Effect[F].delay {
      if (currentRenderNum.get == dem.renderNum) {
        calculateEventPropagation(dem.target, dem.eventType) forall { eventId =>
          topLevelComponentInstance.applyEvent(eventId)
        }
        ()
      }
    }

  frontend
    .browserHistoryMessages
    .foreach(onHistory)
    .runAsyncForget

  frontend
    .domEventMessages
    .foreach(onEvent)
    .runAsyncForget

  val stateStream: Stream[F, (Id, Any)] =
    stateQueue.stream

  val messagesStream: Stream[F, M] =
    messagesQueue.stream

  def destroy(): F[Unit] = for {
    _ <- stateQueue.close()
    _ <- messagesQueue.close()
    _ <- topLevelComponentInstance.destroy()
  } yield ()

  def initialize(reload: Boolean): F[Unit] = {

    // If dev mode is enabled and active
    // CSS should be reloaded
    def reloadCssIfNecessary() =
      if (devMode.isActive) frontend.reloadCss()
      else Effect[F].unit

    // After 'cleanRoot' DiffRenderContext should
    // be noticed about actual DOM state.
    def renderEmptyBody() =
      Effect[F].delay {
        renderContext.openNode(levsha.XmlNs.html, "body")
        renderContext.closeNode("body")
        renderContext.diff(DiffRenderContext.DummyChangesPerformer)
      }

    // Render current state using 'performDiff'.
    def render(performDiff: (ChangesPerformer => Unit) => F[Unit]) =
      for {
        snapshot <- stateManager.snapshot
        _ <- Effect[F].delay(topLevelComponentInstance.applyRenderContext((), renderContext, snapshot))
        _ <- performDiff(renderContext.diff)
        _ <- saveRenderContextIfNecessary()
      } yield ()

    if (reload) {

      // Reload opened page after
      // server restart
      for {
        _ <- frontend.setRenderNum(0)
        _ <- frontend.cleanRoot()
        _ <- reloadCssIfNecessary()
        _ <- renderEmptyBody()
        _ <- onState()
      } yield ()

    } else if (devMode.saved) {

      // Initialize with
      // 1. Old page in users browser
      // 2. Has saved render context
      for {
        _ <- frontend.setRenderNum(0)
        _ <- reloadCssIfNecessary()
        _ <- Effect[F].delay(renderContext.swap())
        // Serialized render context exists.
        // It means that user is looking at page
        // generated by old code. The code may
        // consist changes in render, so we
        // should deliver them to the user.
        _ <- render(frontend.performDomChanges)
      } yield ()

    } else {

      // Initialize with pre-rendered page
      // THIS IS COMMON INITIALIZATION SCENARIO
      for {
        _ <- frontend.setRenderNum(0)
        _ <- reloadCssIfNecessary()
        _ <- render(f => Effect[F].delay(f(DiffRenderContext.DummyChangesPerformer)))
      } yield ()
    }
  }
}
