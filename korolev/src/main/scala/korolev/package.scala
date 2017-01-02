/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
package object korolev extends Shtml {

  // Routing API
  @inline val Root = Router.Root
  @inline val / = Router./
  type / = Router./

  type Render[S] = PartialFunction[S, VDom.Node]
}
