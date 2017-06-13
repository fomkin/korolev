import levsha.{RenderContext, RenderUnit, TemplateDsl}

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
package object korolev {

  // Routing API
  @inline val Root = Router.Root
  @inline val / = Router./
  type / = Router./
}
