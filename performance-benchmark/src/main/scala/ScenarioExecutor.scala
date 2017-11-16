import KorolevConnection.{FromServer, ToServer}
import akka.typed.scaladsl.Actor
import akka.typed.{ActorRef, Behavior, Terminated}

object ScenarioExecutor {

  case class Scenario(name: String, steps: Vector[ScenarioStep]) {
    def newState: ScenarioState = ScenarioState(this, 0)
  }

  case class ScenarioState(scenario: Scenario, step: Int) {

    def current: Option[ScenarioStep] =
      if (endReached) None
      else Some(scenario.steps(step))

    def next: ScenarioState =
      if (endReached) this
      else copy(step = step + 1)

    def endReached: Boolean =
      step == scenario.steps.length
  }

  sealed trait ScenarioStep

  object ScenarioStep {
    case class Send(name: Option[String], value: ToServer) extends ScenarioStep
    case class Expect(name: Option[String], value: FromServer) extends ScenarioStep
  }

  sealed trait Report

  object Report {
    case class Unexpected(state: ScenarioState, expected: FromServer, gotten: FromServer) extends Report
    case class Success(scenario: Scenario, metrics: Map[Int, Long]) extends Report
    case class CantRunScenario(scenario: Scenario) extends Report
    case object MessagesFromClosedConnection extends Report
    case object SuddenlyClosed extends Report
  }

  def apply(scenario: Scenario)(reporter: ActorRef[Report]): Behavior[FromServer] = {

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
        ctx.watch(connection)
        val state = scenario.newState
        state.current match {
          case Some(ScenarioStep.Expect(_, expect)) =>
            await(System.nanoTime(), Map(), state, expect, connection)
          case Some(_: ScenarioStep.Send) =>
            sendUntilExpectAndReport(state, Map.empty, connection)
          case None =>
            reporter ! Report.CantRunScenario(scenario)
            Actor.stopped
        }
      case (_, _) =>
        reporter ! Report.MessagesFromClosedConnection
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
