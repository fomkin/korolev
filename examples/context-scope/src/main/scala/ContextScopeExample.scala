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
  import symbolDsl._

  val blogView = new BlogView(
    context.scope(
      read = { case ViewState(_, s: Blog) => s },
      write = { case (orig, s) => orig.copy(tab = s) }
    )
  )

  val service: AkkaHttpService = akkaHttpService {
    KorolevServiceConfig[Future, ViewState, Any] (
      router = Router.empty,
      stateStorage = StateStorage.default(ViewState("My blog", Blog.default)), // TODO
      render = {
        case state =>
          'body(
            'h1(state.blogName),
            'div(
              'div(
                if (state.tab.isInstanceOf[Blog]) Seq(
                  'fontWeight @= "bold",
                  'borderBottom @= "1px solid black",
                ) else void,
                event('click)(access => access.transition(_.copy(tab = Blog.default))),
                'padding @= 5,
                'display @= "inline-block",
                "Blog"
               ),
              'div(
                if (state.tab.isInstanceOf[About]) Seq(
                  'fontWeight @= "bold",
                  'borderBottom @= "1px solid black",
                ) else void,
                event('click)(access => access.transition(_.copy(tab = About.default))),
                'padding @= 5,
                'display @= "inline-block",
                "About"
              )
            ),
            'div(
              'marginTop @= 20,
              state.tab match {
                case blog: Blog => blogView(blog)
                case about: About => 'p(about.text)
              }
            )
          )
      }
    )
  }
}

