package korolev.data

sealed trait Report

object Report {
  case class Unexpected(state: ScenarioState, expected: FromServer, gotten: FromServer) extends Report
  case class Success(scenario: Scenario, metrics: Map[Int, Long]) extends Report
  case class CantRunScenario(scenario: Scenario) extends Report
  case class MessagesFromClosedConnection(message: FromServer) extends Report
  case object SuddenlyClosed extends Report
}
