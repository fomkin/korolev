package korolev

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, Terminated}
import korolev.data._

import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration

object ScenarioExecutor {

  def apply(scenario: Scenario,
            reporter: ActorRef[Report],
            delay: Option[FiniteDuration] = None): Behavior[FromServer] = {
    @tailrec
    def sendUntilExpect(state: ScenarioState,
                        connection: ActorRef[ToServer]): (ScenarioState, Option[ScenarioStep.Expect]) = {
      state.current match {
        case Some(ScenarioStep.Send(_, message)) =>
          connection ! message
          sendUntilExpect(state.next, connection)
        case Some(expect: ScenarioStep.Expect) => (state, Some(expect))
        case None                              => (state, None)
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
          Behaviors.stopped
      }
    }

    def await(nanos: Long,
              currMetrics: Map[Int, Long],
              currState: ScenarioState,
              expected: FromServer,
              connection: ActorRef[ToServer]): Behavior[FromServer] = {

      Behaviors.receiveMessagePartial[FromServer] {
        case `expected` =>
          val dt = System.nanoTime() - nanos
          val metrics = currMetrics + (currState.step -> dt)
          sendUntilExpectAndReport(currState.next, metrics, connection)
        case unexpected =>
          reporter ! Report.Unexpected(currState, expected, unexpected)
          Behaviors.stopped
      }
    }

    Behaviors.setup[FromServer] { ctx =>
      Behaviors
        .receiveMessagePartial[FromServer] {
          case FromServer.Connected(connection) =>
            val state = scenario.newState
            val connectionWithDelay = delay.fold(connection) { delay =>
              ctx.spawnAnonymous {
                Behaviors.setup[ToServer] { ctx =>
                  val internal = ctx.spawnAnonymous {
                    Behaviors.withTimers[Either[Unit, ToServer]] { timers =>
                      def aux(buffer: List[ToServer]): Behavior[Either[Unit, ToServer]] =
                        Behaviors.receiveMessagePartial {
                          case Left(_) =>
                            buffer.reverse.foreach(connection ! _)
                            aux(Nil)
                          case Right(message) =>
                            aux(message :: buffer)
                        }

                      timers.startTimerAtFixedRate("default", Left(()), delay)

                      aux(Nil)
                    }
                  }
                  Behaviors.receiveMessage[ToServer] { message =>
                    internal ! Right(message)
                    Behaviors.same
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
                Behaviors.stopped
            }
          case message =>
            reporter ! Report.MessagesFromClosedConnection(message)
            Behaviors.stopped
        }
        .receiveSignal {
          case (_, Terminated(_)) ⇒
            reporter ! Report.SuddenlyClosed
            Behaviors.stopped
        }
    }
  }

  // bridge.js:\d+ -> \[(\d+),(.+)\]
  // ScenarioStep.Expect(None, FromServer.Procedure($1, List($2))),

  // bridge.js:\d+ <- \[(\d+),(.+)\]
  // ScenarioStep.Send(None, ToServer.Callback($1, $2)),
}