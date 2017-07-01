/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
package object korolev {

  // Routing API
  @inline val Root = Router.Root
  @inline val / = Router./
  type / = Router./

  @deprecated("Use ApplicationContext instead of Effects", "0.4.0")
  val Effects = ApplicationContext
}
