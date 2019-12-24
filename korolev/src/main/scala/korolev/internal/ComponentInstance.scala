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

import korolev._
import korolev.effect.{Effect, Queue, Reporter}
import korolev.effect.syntax._
import korolev.execution.Scheduler
import korolev.state.{StateDeserializer, StateManager, StateSerializer}
import levsha.Document.Node
import levsha.{Id, StatefulRenderContext, XmlNs}
import levsha.events.EventId

import scala.collection.mutable
import Context._
import korolev.effect.io.LazyBytes

/**
  * Component state holder and effects performer
  *
  * Performing cycle:
  *
  * 1. prepare()
  * 2. Optionally setState()
  * 3. applyRenderContext()
  * 4. dropObsoleteMisc()
  *
  * @tparam AS Type of top level state (application state)
  * @tparam CS Type of component state
  */
final class ComponentInstance
  [
    F[_]: Effect: Scheduler,
    AS: StateSerializer: StateDeserializer, M,
    CS: StateSerializer: StateDeserializer, P, E
  ](
     nodeId: Id,
     sessionId: Qsid,
     frontend: Frontend[F],
     eventRegistry: EventRegistry[F],
     stateManager: StateManager[F],
     getRenderNum: () => Int,
     val component: Component[F, CS, P, E],
     notifyStateChange: (Id, Any) => F[Unit],
     reporter: Reporter
  ) { self =>

  import ComponentInstance._
  import reporter.Implicit

  private val miscLock = new Object()

  private val markedDelays = mutable.Set.empty[Id] // Set of the delays which are should survive
  private val markedComponentInstances = mutable.Set.empty[Id]
  private val delays = mutable.Map.empty[Id, DelayInstance[F, CS, E]]
  private val elements = mutable.Map.empty[ElementId[F], Id]
  private val events = mutable.Map.empty[EventId, Event[F, CS, E]]
  private val nestedComponents = mutable.Map.empty[Id, ComponentInstance[F, CS, E, _, _, _]]

  // Why we use '() => F[Unit]'? Because should
  // support scala.concurrent.Future which is has
  // strict semantic (runs immediately).
  private val immediatePendingEffects = Queue[F, () => F[Unit]]()

  @volatile private var eventSubscription = Option.empty[E => _]

  private[korolev] object browserAccess extends Access[F, CS, E] {

    private def getId(elementId: ElementId[F]): F[Id] = Effect[F].delay {
      // miscLock synchronization required
      // because prop handler methods can be
      // invoked during render.
      miscLock.synchronized {
        elements.get(elementId) match {
          case None => throw new Exception("No element matched for accessor")
          case Some(id) => id
        }
      }
    }

    def property(elementId: ElementId[F]): PropertyHandler[F] = {
      val idF = getId(elementId)
      new PropertyHandler[F] {
        def get(propName: Symbol): F[String] =
          get(propName.name)

        def set(propName: Symbol, value: Any): F[Unit] =
          set(propName.name, value)

        def get(propName: String): F[String] = idF.flatMap { id =>
          frontend.extractProperty(id, propName)
        }

        def set(propName: String, value: Any): F[Unit] = idF.flatMap { id =>
          // XmlNs argument is empty cause it will be ignored
          frontend.setProperty(id, propName, value)
        }
      }
    }

    def focus(element: ElementId[F]): F[Unit] =
      getId(element).flatMap { id =>
        frontend.focus(id)
      }

    def publish(message: E): F[Unit] =
      Effect[F].delay(eventSubscription.foreach(f => f(message)))

    def state: F[CS] = {
      val state = stateManager.read[CS](nodeId)

      state.map(_.getOrElse(throw new RuntimeException("State is empty")))
    }

    def sessionId: F[Qsid] = Effect[F].delay(self.sessionId)

    def transition(f: Transition[CS]): F[Unit] = applyTransition(f)

    def downloadFormData(element: ElementId[F]): F[FormData] =
      for {
        id <- getId(element)
        formData <- frontend.uploadForm(id)
      } yield formData

    def downloadFiles(id: ElementId[F]): F[List[File[Array[Byte]]]] = {
      downloadFilesAsStream(id).flatMap { lazyFileList =>
        Effect[F].sequence {
          lazyFileList.map { lazyFile =>
            lazyFile.data.toStrict.map(x => File(lazyFile.name, x))
          }
        }
      }
    }

    def downloadFilesAsStream(elementId: ElementId[F]): F[List[File[LazyBytes[F]]]] =
      for {
        id <- getId(elementId)
        streams <- frontend.uploadFiles(id)
      } yield streams

    def resetForm(elementId: ElementId[F]): F[Unit] =
      getId(elementId).flatMap { id =>
        frontend.resetForm(id)
      }

    def evalJs(code: String): F[String] = frontend.evalJs(code)

    def eventData: F[String] = frontend.extractEventData(getRenderNum())
  }

  /**
    * Subscribes to component instance events.
    * Callback will be invoked on call of `access.publish()` in the
    * component instance context.
    */
  def setEventsSubscription(callback: E => _): Unit = {
    eventSubscription = Some(callback)
  }

  def applyRenderContext(parameters: P,
                         rc: StatefulRenderContext[Binding[F, AS, M]],
                         snapshot: StateManager.Snapshot): Unit = miscLock.synchronized {
    // Reset all event handlers delays and elements
    prepare()
    val state = snapshot[CS](nodeId).getOrElse(component.initialState)
    val node =
      try {
        component.render(parameters, state)
      } catch {
        case e: MatchError =>
          Node[Binding[F, CS, E]] { rc =>
            reporter.error(s"Render is not defined for $state", e)
            rc.openNode(XmlNs.html, "span")
            rc.addTextNode("Render is not defined for the state")
            rc.closeNode("span")
          }
      }
    val proxy = new StatefulRenderContext[Binding[F, CS, E]] { proxy =>
      def subsequentId: Id = rc.subsequentId
      def currentId: Id = rc.currentId
      def currentContainerId: Id = rc.currentContainerId
      def openNode(xmlNs: XmlNs, name: String): Unit = rc.openNode(xmlNs, name)
      def closeNode(name: String): Unit = rc.closeNode(name)
      def setAttr(xmlNs: XmlNs, name: String, value: String): Unit = rc.setAttr(xmlNs, name, value)
      def setStyle(name: String, value: String): Unit = rc.setStyle(name, value)
      def addTextNode(text: String): Unit = rc.addTextNode(text)
      def addMisc(misc: Binding[F, CS, E]): Unit = {
        misc match {
          case event @ Event(eventType, phase, _) =>
            val id = rc.currentContainerId
            events.put(EventId(id, eventType, phase), event)
            eventRegistry.registerEventType(event.`type`)
          case element: ElementId[F] =>
            val id = rc.currentContainerId
            elements.put(element, id)
            ()
          case delay: Delay[F, CS, E] =>
            val id = rc.currentContainerId
            markedDelays += id
            if (!delays.contains(id)) {
              val delayInstance = new DelayInstance(delay, reporter)
              delays.put(id, delayInstance)
              delayInstance.start(browserAccess)
            }
          case entry @ ComponentEntry(_, _: Any, _: ((Access[F, CS, E], Any) => F[Unit])) =>
            val id = rc.subsequentId
            nestedComponents.get(id) match {
              case Some(n: ComponentInstance[F, CS, E, Any, Any, Any]) if n.component.id == entry.component.id =>
                // Use nested component instance
                markedComponentInstances += id
                n.setEventsSubscription((e: Any) => entry.eventHandler(browserAccess, e).runAsyncForget)
                n.applyRenderContext(entry.parameters, proxy, snapshot)
              case _ =>
                val n = entry.createInstance(
                  id, sessionId, frontend, eventRegistry,
                  stateManager, getRenderNum, notifyStateChange,
                  reporter
                )
                markedComponentInstances += id
                nestedComponents.put(id, n)
                n.setEventsSubscription((e: Any) => entry.eventHandler(browserAccess, e).runAsyncForget)
                n.applyRenderContext(entry.parameters, proxy, snapshot)
            }
        }
      }
    }
    node(proxy)
  }

  def applyTransition(transition: Transition[CS]): F[Unit] = {
    val effect = () =>
      for {
        state <- stateManager.read[CS](nodeId)
        newState = transition(state.getOrElse(component.initialState))
        _ <- stateManager.write(nodeId, newState)
        _ <- notifyStateChange(nodeId, newState)
      } yield ()
    immediatePendingEffects.offer(effect)
  }

  def applyEvent(eventId: EventId): Boolean = {
    events.get(eventId) match {
      case Some(event: Event[F, CS, E]) =>
        // A user defines the event effect, so we
        // don't control the time of execution.
        // We shouldn't block the application if
        // the user's code waits for something
        // for a long time.
        event.effect(browserAccess).runAsyncForget
        false
      case None =>
        nestedComponents.values.forall { nested =>
          nested.applyEvent(eventId)
        }
    }
  }

  /**
    * Remove all delays and nested component instances
    * which were not marked during applying render context.
    */
  def dropObsoleteMisc(): Unit = miscLock.synchronized {
    delays foreach {
      case (id, delay) =>
        if (!markedDelays.contains(id)) {
          delays.remove(id)
          delay.cancel()
        }
    }
    nestedComponents foreach {
      case (id, nested) =>
        if (!markedComponentInstances.contains(id)) {
          nestedComponents.remove(id)
          stateManager.delete(id).runAsyncForget
        }
        else nested.dropObsoleteMisc()
    }
  }

  /**
    * Prepares component instance to applying render context.
    * Removes all temporary and obsolete misc.
    * All nested components also will be prepared.
    */
  private def prepare(): Unit = {
    markedComponentInstances.clear()
    markedDelays.clear()
    elements.clear()
    events.clear()
    // Remove only finished delays
    delays foreach {
      case (id, delay) =>
        if (delay.isFinished)
          delays.remove(id)
    }
  }

  /**
    * Close 'immediatePendingEffects' in this component and
    * all nested components.
    *
    * MUST be invoked after closing connection.
    */
  def destroy(): F[Unit] =
    for {
      _ <- immediatePendingEffects.close()
      _ <- nestedComponents
        .values
        .toList
        .map(_.destroy())
        .sequence
        .unit
    } yield ()

  // Execute effects sequentially
  immediatePendingEffects
    .stream
    .foreach(_.apply())
    .runAsyncForget
}

private object ComponentInstance {

  import Context.Access
  import Context.Delay

  final class DelayInstance[F[_]: Effect: Scheduler, S, M](delay: Delay[F, S, M], reporter: Reporter) {

    import reporter.Implicit

    @volatile private var handler = Option.empty[Scheduler.JobHandler[F, _]]
    @volatile private var finished = false

    def isFinished: Boolean = finished

    def cancel(): Unit = {
      handler.foreach(_.cancel())
    }

    def start(access: Access[F, S, M]): Unit = {
      handler = Some {
        Scheduler[F].scheduleOnce(delay.duration) {
          finished = true
          delay.effect(access).runAsyncForget
        }
      }
    }
  }
}
