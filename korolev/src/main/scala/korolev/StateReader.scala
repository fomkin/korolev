package korolev

import levsha.Id

trait StateReader {
  def read[T](node: Id): Option[T]
}
