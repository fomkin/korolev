package korolev

import levsha.Id

trait StateReader {
  def topLevel[T]: T
  def read[T](node: Id): Option[T]
}

object StateReader {

  private final class WithTopLevelState(state: Any) extends StateReader {
    def topLevel[T]: T = state.asInstanceOf[T]
    def read[T](node: Id): Option[T] = None
  }

  def withTopLevelState(state: Any): StateReader =
    new WithTopLevelState(state)
}
