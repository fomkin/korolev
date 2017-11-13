package object korolev {

  // Routing API
  @inline val Root = Router.Root
  @inline val / = Router./

  @deprecated("Use Context instead of Effects", "0.6.0")
  val Effects = Context

  type Transition[State] = State => State
}
