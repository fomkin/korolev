package korolev.testkit

import korolev.Context.ElementId

sealed trait Action[+F[_], +S, +M]

object Action {

  case class Property(element: ElementId, name: String, value: String) extends Action[Nothing, Nothing, Nothing]
  case class Focus(element: ElementId) extends Action[Nothing, Nothing, Nothing]
  case class Transition[T](newState: T) extends Action[Nothing, T, Nothing]
  case class ResetForm(element: ElementId) extends Action[Nothing, Nothing, Nothing]
  case class EvalJs(result: Either[Throwable, String]) extends Action[Nothing, Nothing, Nothing]
  case class Publish[T](message: T) extends Action[Nothing, Nothing, T]
  case class RegisterCallback[F[_]](name: String, f: String => F[Unit]) extends Action[F, Nothing, Nothing]

//  sealed trait Probe[+T]
//
//  object Probe {
//    case class Property(element: ElementId, f: (String, String) => Boolean) extends Probe[Nothing]
//    case class Focus(element: ElementId) extends Probe[Nothing]
//    case class Transition[T](f: T => Boolean) extends Probe[T]
//    case class ResetForm(element: ElementId) extends Probe[Nothing]
//    case class EvalJs(f: String => Boolean) extends Probe[Nothing]
//  }
}
