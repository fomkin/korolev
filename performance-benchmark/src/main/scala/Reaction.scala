import KorolevIncomingMessage.{FunctionCall, GetAndSaveAs, RegisterCallback}
import fs2.Task

object Reaction {

  type Reaction = PartialFunction[KorolevIncomingMessage, Task[Seq[Any]]]

  val defaultReaction: Reaction = {
    case FunctionCall(id, _, _, _) => Task.now(Seq(id, true, "@unit"))
    case RegisterCallback(id, ref) => Task.now(Seq(id, true, s"@obj:$ref"))
    case GetAndSaveAs(id, _, _, savedRef) => Task.now(Seq(id, true, s"@obj:$savedRef"))
  }

  def reaction(message: KorolevIncomingMessage)(pf: Reaction): Task[Seq[Any]] = {
    val f = pf orElse defaultReaction
    f(message)
  }

}
