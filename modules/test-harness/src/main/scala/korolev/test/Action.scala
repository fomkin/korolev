package korolev.test

import korolev.Context.ElementId

sealed trait Action[+T]

object Action {

  case class Property(element: ElementId, name: String, value: String) extends Action[Nothing]
  case class Focus(element: ElementId) extends Action[Nothing]
  case class Transition[T](newState: T) extends Action[T]
  case class ResetForm(element: ElementId) extends Action[Nothing]
  case class EvalJs(code: String) extends Action[Nothing]

  sealed trait Probe[+T]
  
  object Probe {
    case class Property(element: ElementId, f: (String, String) => Boolean) extends Probe[Nothing]
    case class Focus(element: ElementId) extends Probe[Nothing]
    case class Transition[T](f: T => Boolean) extends Probe[T]
    case class ResetForm(element: ElementId) extends Probe[Nothing]
    case class EvalJs(f: String => Boolean) extends Probe[Nothing]
  }
}
