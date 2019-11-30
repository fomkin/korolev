package korolev.data

case class Scenario(name: String, steps: Vector[ScenarioStep]) {
  def newState: ScenarioState = ScenarioState(this, 0)
}

object Scenario
