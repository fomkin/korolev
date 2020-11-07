import korolev._
import korolev.akka._

import scala.concurrent.ExecutionContext.Implicits.global
import korolev.server._
import korolev.state.javaSerialization._
import korolev.web.Uri.OptionQueryParam
import korolev.web.Uri

import scala.concurrent.Future

object UriRoutingExample extends SimpleAkkaHttpKorolevApp {
  object BeginOptionQueryParam extends OptionQueryParam("begin")
  object EndOptionQueryParam extends OptionQueryParam("end")

  case class State(begin: Option[String] = None, end: Option[String] = None)

  object State {
    val globalContext = Context[Future, State, Any]
  }

  import State.globalContext._

  import levsha.dsl._
  import html._
  import korolev.web.Path._
  import korolev.web.Uri._

  val beginElementId = elementId()
  val endElementId = elementId()

  val service = akkaHttpService {
    KorolevServiceConfig[Future, State, Any](
      stateLoader = StateLoader.default(State()),
      document = state =>
        optimize {
          Html(
            head(
              title(s"Search form example")
            ),
            body(
              div("Enter search parameters and look to URI"),
              p(),
              div(
                form(
                  input(
                    beginElementId,
                    `type` := "text",
                    placeholder := "Enter begin",
                    state.begin.map(begin => value := begin)
                  ),
                  input(
                    endElementId,
                    `type` := "text",
                    placeholder := "Enter end",
                    state.end.map(end => value := end)
                  ),
                  button(
                    "Search!",
                    event("click"){access =>
                      for {
                        begin <- access.valueOf(beginElementId)
                        end <- access.valueOf(endElementId)
                        _ <- access.transition { s =>
                          s.copy(begin = trimToEmpty(begin), end = trimToEmpty(end))
                        }
                      } yield ()
                    }
                  )
                )
              )
            )
          )
      },
      router = Router(
        fromState = {
          case State(begin, end) =>
            Uri(Root / "search").withParam("begin", begin).withParam("end", end)
        },
        toState = {
          case Root :? _ =>
            initialState =>
              Future.successful(initialState)
          case Root  / "search" :? BeginOptionQueryParam(begin) +& EndOptionQueryParam(end) => _ =>
              val result = State(begin, end)
              Future.successful(result)
        }
      )
    )
  }

  private def trimToEmpty(value: String): Option[String] = {
    if (value.isBlank) {
      None
    } else {
      Some(value)
    }
  }
}