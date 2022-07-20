package korolev.engine

import korolev.context.{CII, State}

case class ViewState(appStates: Map[State[_], Any],
                     componentStates: Map[(CII, State[_]), Any]) {

  def add[T](state: State[T], maybeCii: Option[CII], value: T): ViewState = {
    maybeCii match {
      case None => this.copy(appStates = this.appStates + (state -> value))
      case Some(cii) => this.copy(componentStates = this.componentStates + ((cii, state) -> value))
    }

  }

  def take[T](state: State[T], maybeCii: Option[CII]): T =
    maybeCii match {
      case None =>
        appStates
          .getOrElse(state, state.empty)
          .asInstanceOf[T]
      case Some(cii) =>
        componentStates
          .getOrElse((cii, state), state.empty)
          .asInstanceOf[T]
    }
}
