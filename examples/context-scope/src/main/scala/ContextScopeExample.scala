import ViewState.Tab.{About, Blog}

import korolev._
import korolev.akkahttp._
import korolev.execution._
import korolev.server._
import korolev.state.javaSerialization._

import scala.concurrent.Future

object ContextScopeExample extends SimpleAkkaHttpKorolevApp {

  val context = Context[Future, ViewState, Any]

  import context._
  import levsha.dsl._
  import html._

  val blogView = new BlogView(
    context.scope(
      read = { case ViewState(_, s: Blog) => s },
      write = { case (orig, s) => orig.copy(tab = s) }
    )
  )

  val service: AkkaHttpService = akkaHttpService {
    KorolevServiceConfig[Future, ViewState, Any] (
      router = Router.empty,
      stateStorage = StateStorage.default(ViewState("My blog", Blog.default)),
      render = {
        case state =>
          val isBlog = state.tab.isInstanceOf[Blog]
          val isAbout = state.tab.isInstanceOf[About]

          optimize {
            body(
              h1(state.blogName),
              div(
                div(
                  when(isBlog)(fontWeight @= "bold"),
                  when(isBlog)(borderBottom @= "1px solid black"),
                  event("click")(access => access.transition(_.copy(tab = Blog.default))),
                  padding @= "5px",
                  display @= "inline-block",
                  "Blog"
                ),
                div(
                  when(isAbout)(fontWeight @= "bold"),
                  when(isAbout)(borderBottom @= "1px solid black"),
                  event("click")(access => access.transition(_.copy(tab = About.default))),
                  padding @= "5px",
                  display @= "inline-block",
                  "About"
                )
              ),
              div(
                marginTop @= "20px",
                state.tab match {
                  case blog: Blog => blogView(blog)
                  case about: About => p(about.text)
                }
              )
            )
          }
      }
    )
  }
}

