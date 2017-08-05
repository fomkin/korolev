package tools

sealed trait StepResult

object StepResult {
  case class CowardlySkipped(reason: String) extends StepResult
  case class Error(cause: Throwable) extends StepResult
  case object Ok extends StepResult
}
