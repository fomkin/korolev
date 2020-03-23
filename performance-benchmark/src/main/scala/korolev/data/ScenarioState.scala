package korolev.data

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

object ScenarioState
