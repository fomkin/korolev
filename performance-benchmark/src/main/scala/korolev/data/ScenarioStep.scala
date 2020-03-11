package korolev.data

sealed trait ScenarioStep

object ScenarioStep {
  case class Send(name: Option[String], value: ToServer) extends ScenarioStep
  case class Expect(name: Option[String], value: FromServer) extends ScenarioStep
}
