import akka.typed.scaladsl.Actor
import akka.typed.{ActorRef, Behavior, Terminated}
import data._

import scala.concurrent.duration.FiniteDuration

object ScenarioExecutor {

  def apply(scenario: Scenario,
            reporter: ActorRef[Report],
            delay: Option[FiniteDuration] = None): Behavior[FromServer] = {

    def sendUntilExpect(state: ScenarioState,
      connection: ActorRef[ToServer]): (ScenarioState, Option[ScenarioStep.Expect]) = {
      state.current match {
        case Some(ScenarioStep.Send(_, message)) =>
          connection ! message
          sendUntilExpect(state.next, connection)
        case Some(expect: ScenarioStep.Expect) => (state, Some(expect))
        case None => (state, None)
      }
    }

    def sendUntilExpectAndReport(currentState: ScenarioState,
                                 metrics: Map[Int, Long],
                                 connection: ActorRef[ToServer]): Behavior[FromServer] = {
      sendUntilExpect(currentState, connection) match {
        case (state, Some(next)) =>
          await(System.nanoTime(), metrics, state, next.value, connection)
        case (state, None) =>
          reporter ! Report.Success(state.scenario, metrics)
          connection ! ToServer.Close
          Actor.stopped
      }
    }

    def await(nanos: Long,
              currMetrics: Map[Int, Long],
              currState: ScenarioState,
              expected: FromServer,
              connection: ActorRef[ToServer]): Behavior[FromServer] = {

      Actor.immutable[FromServer] {
        case (_, `expected`) =>
          val dt = System.nanoTime() - nanos
          val metrics = currMetrics + (currState.step -> dt)
          sendUntilExpectAndReport(currState.next, metrics, connection)
        case (_, unexpected) =>
          reporter ! Report.Unexpected(currState, expected, unexpected)
          Actor.stopped
      }
    }

    Actor.immutable[FromServer] {
      case (ctx, FromServer.Connected(connection)) =>
        val state = scenario.newState
        val connectionWithDelay = delay.fold(connection) { delay =>
          ctx.spawnAnonymous {
            Actor.deferred[ToServer] { ctx =>
              val internal = ctx.spawnAnonymous {
                Actor.withTimers[Either[Unit, ToServer]] { scheduler =>
                  def aux(buffer: List[ToServer]): Behavior[Either[Unit, ToServer]] = Actor.immutable {
                    case (_, Left(_)) =>
                      buffer.reverse.foreach(connection ! _)
                      aux(Nil)
                    case (_, Right(message)) =>
                      aux(message :: buffer)
                  }
                  scheduler.startPeriodicTimer("default", Left(()), delay)
                  aux(Nil)
                }
              }
              Actor.immutable[ToServer] { (ctx, message) =>
                internal ! Right(message)
                Actor.same
              }
            }
          }
        }
        ctx.watch(connection)
        state.current match {
          case Some(ScenarioStep.Expect(_, expect)) =>
            await(System.nanoTime(), Map(), state, expect, connectionWithDelay)
          case Some(_: ScenarioStep.Send) =>
            sendUntilExpectAndReport(state, Map.empty, connectionWithDelay)
          case None =>
            reporter ! Report.CantRunScenario(scenario)
            Actor.stopped
        }
      case (_, message) =>
        reporter ! Report.MessagesFromClosedConnection(message)
        Actor.stopped
    } onSignal {
      case (_, Terminated(_)) ⇒
        reporter ! Report.SuddenlyClosed
        Actor.stopped
    }
  }

  // bridge.js:\d+ -> \[(\d+),(.+)\]
  // ScenarioStep.Expect(None, FromServer.Procedure($1, List($2))),

  // bridge.js:\d+ <- \[(\d+),(.+)\]
  // ScenarioStep.Send(None, ToServer.Callback($1, $2)),
}
