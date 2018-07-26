package korolev.state

import korolev.Async
import levsha.Id

abstract class StateManager[F[+_]: Async] {
  def snapshot: F[StateManager.Snapshot]
  def read[T: StateDeserializer](nodeId: Id): F[Option[T]]
  def delete(nodeId: Id): F[Unit]
  def write[T: StateSerializer](nodeId: Id, value: T): F[Unit]
}

object StateManager {

  @deprecated("Use korolev.Transition", "0.6.0")
  type Transition[State] = State => State

  trait Snapshot {
    def apply[T: StateDeserializer](nodeId: Id): Option[T]
  }
}
