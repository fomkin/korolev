package korolev.engine

import korolev.context.Access.AccessException
import korolev.context.Binding
import levsha.Id
import levsha.events.EventId

import scala.collection.mutable

case class RenderState[F[_]](elements: mutable.Map[Binding.Element, Id],
                             events: mutable.Map[EventId, Vector[Binding.Event[F]]],
                             renderNum: Int) {

  def getId(element: Binding.Element): Id =
    elements.getOrElse(element, throw new AccessException.ElementBindingException(element))
}
