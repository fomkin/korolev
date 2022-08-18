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

package korolev.internal

import java.util.concurrent.atomic.AtomicInteger
import korolev.Context.*
import korolev.effect.syntax.*
import korolev.*
import korolev.effect.{Effect, Hub, Queue, Reporter, Scheduler, Stream}
import korolev.internal.Frontend.DomEventMessage
import korolev.state.{StateDeserializer, StateManager, StateSerializer}
import korolev.web.{Path, PathAndQuery}
import levsha.events.calculateEventPropagation
import levsha.impl.DiffRenderContext
import levsha.impl.DiffRenderContext.ChangesPerformer
import levsha.{Document, Id, StatefulRenderContext, XmlNs}

import scala.concurrent.ExecutionContext

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
     rootPath: Path,
     router: Router[F, S],
     createMiscProxy: (StatefulRenderContext[Binding[F, S, M]], (StatefulRenderContext[Binding[F, S, M]], Binding[F, S, M]) => Unit) => StatefulRenderContext[Binding[F, S, M]],
     scheduler: Scheduler[F],
     reporter: Reporter,
     upgradeEventEffect: Context.Access[F, S, M] => F[Unit] => F[Unit]
  ) { application =>

  import reporter.Implicit

  private val devMode = new DevMode.ForRenderContext(sessionId.toString)
  private val currentRenderNum = new AtomicInteger(0)
  private val stateQueue = Queue[F, (Id, Any, Option[Effect.Promise[Unit]])]()
  private val stateHub = Hub(stateQueue.stream)
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
              rc.openNode(XmlNs.html, "html")
              rc.openNode(XmlNs.html, "body")
              rc.addTextNode("Render is not defined for the state. ")
              rc.addTextNode(e.getMessage())
              rc.closeNode("body")
              rc.closeNode("html")
            }
        }
      }
    }
    val componentInstance = new ComponentInstance[F, S, M, S, Any, M](
      Id.TopLevel, sessionId, frontend, eventRegistry,
      stateManager, () => currentRenderNum.get(), component,
      stateQueue, createMiscProxy, scheduler, reporter,
      upgradeEventEffect
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

  private def onState(maybeRenderCallback: Option[Effect.Promise[Unit]]): F[Unit] =
    for {
      snapshot <- stateManager.snapshot
      // Set page url if router exists
      _ <- router.fromState
        .lift(snapshot(Id.TopLevel).getOrElse(initialState))
        .fold(Effect[F].unit)(uri => frontend.changePageUrl(rootPath ++ uri))
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
      _ = maybeRenderCallback.foreach(_(Right(())))
    } yield ()


  private def onHistory(pq: PathAndQuery): F[Unit] =
    stateManager
      .read[S](Id.TopLevel)
      .flatMap { maybeTopLevelState =>
        router
          .toState
          .lift(pq)
          .fold(Effect[F].delay(Option.empty[S]))(_(maybeTopLevelState.getOrElse(initialState)).map(Some(_)))
      }
      .flatMap {
        case Some(newState) =>
          stateManager
            .write(Id.TopLevel, newState)
            .after(stateQueue.enqueue(Id.TopLevel, newState, None))
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

  private final val internalStateStream =
    stateHub.newStreamUnsafe()

  val stateStream: F[Stream[F, (Id, Any)]] =
    stateHub.newStream().map { stream =>
      stream.map {
        case (id, state, _) =>
          (id, state)
      }
    }

  val messagesStream: Stream[F, M] =
    messagesQueue.stream

  def destroy(): F[Unit] = for {
    _ <- stateQueue.close()
    _ <- messagesQueue.close()
    _ <- topLevelComponentInstance.destroy()
  } yield ()

  def initialize()(implicit ec: ExecutionContext): F[Unit] = {

    // If dev mode is enabled and active
    // CSS should be reloaded
    def reloadCssIfNecessary() =
      if (devMode.isActive) frontend.reloadCss()
      else Effect[F].unit

    // Render current state using 'performDiff'.
    def render(performDiff: (ChangesPerformer => Unit) => F[Unit]) =
      for {
        snapshot <- stateManager.snapshot
        _ <- Effect[F].delay(topLevelComponentInstance.applyRenderContext((), renderContext, snapshot))
        _ <- performDiff(renderContext.diff)
        _ <- saveRenderContextIfNecessary()
      } yield ()

    if (devMode.saved) {

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
        _ <- topLevelComponentInstance.initialize()
      } yield ()

    } else {

      // Initialize with pre-rendered page
      for {
        _ <- frontend.setRenderNum(0)
        _ <- reloadCssIfNecessary()
        _ <- render(f => Effect[F].delay(f(DiffRenderContext.DummyChangesPerformer)))
        // Start handlers
        _ <- frontend
          .browserHistoryMessages
          .foreach(onHistory)
          .start
        _ <- frontend
          .domEventMessages
          .foreach(onEvent)
          .start
        _ <- internalStateStream
          .foreach {
            case (_, _, cb) =>
              onState(cb)
          }
          .start
        // Init component
        _ <- topLevelComponentInstance.initialize()
      } yield ()
    }
  }
}
