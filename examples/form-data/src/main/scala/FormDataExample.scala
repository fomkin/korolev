import korolev._
import korolev.akka.{AkkaHttpServerConfig, SimpleAkkaHttpKorolevApp}
import korolev.akka._
import korolev.server._

import scala.concurrent.ExecutionContext.Implicits.global
import korolev.state.javaSerialization._
import korolev.web.FormData
import levsha.XmlNs

import scala.concurrent.Future

object FormDataExample extends SimpleAkkaHttpKorolevApp(AkkaHttpServerConfig(maxRequestBodySize = 20 * 1024 * 1024)) {

  import State.globalContext._
  import levsha.dsl._
  import html._

  val role = AttrDef(XmlNs.html, "role")

  val myForm = elementId()
  val pictureFieldName = "picture"
  val textFieldName = "text"
  val multiLineText = "multiLineText"

  val service = akkaHttpService{
    KorolevServiceConfig[Future, State, Any](
      stateLoader = StateLoader.default(State()),
      document = { state =>
        Html(
          head(
            link(
              rel :="stylesheet",
              href :="https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0-alpha.6/css/bootstrap.min.css",
              integrity := "sha384-rwoIResjU2yc3z8GV/NPeZWAv56rSmLldC3R/AZzGRnGxQQKnKkoFVhFQhNUwEyJ",
              crossorigin := "anonymous"
            ),
            style("body { margin: 2em }"),
            script(
              src := "https://code.jquery.com/jquery-3.1.1.slim.min.js",
              integrity := "sha384-A7FZj7v+d/sdmMqp/nOQwliLvUsJfDHW+k9Omg/a/EheAdgtzNs3hpfag6Ed950n",
              crossorigin := "anonymous"
            ),
            script(
              src := "https://cdnjs.cloudflare.com/ajax/libs/tether/1.4.0/js/tether.min.js",
              integrity := "sha384-DztdAPBWPRXSA/3eYEEUWrWCy7G5KFbe8fFjk5JAIxUYHKkDx6Qin1DkWx51bBrb",
              crossorigin := "anonymous"
            ),
            script(
              src := "https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0-alpha.6/js/bootstrap.min.js",
              integrity := "sha384-vBWWzlZJ8ea9aCX4pEW3rVHjgjt7zpkNpZk+02D9phzyeVkE+jo0ieGizqPLForn",
              crossorigin := "anonymous"
            )
          ),
          body (
            form (`class` := "card",
              myForm,
              div (
                `class` := "card-block",
                legend ("FormData Example"),
                p (
                  label ("The text"),
                  input (`type` := "text", name := textFieldName)
                ),
                p (
                  label ("The text area"),
                  textarea (name := multiLineText)
                ),
                p (
                  button ("Submit")
                )
              ),
              event("submit") { access =>
                for {
                  formData <- access.downloadFormData(myForm)
                  _ <- access.resetForm(myForm)
                  _ <- access.transition(_ => State(Some(formData), None))
                } yield ()
              }
            ),
            state.formData match {
              case Some(formData) =>
                table(
                  tbody(
                    formData.content map { entry =>
                      tr(
                        td(entry.name),
                        td(entry.asString)
                      )
                    }
                  )
                )
              case None =>
                div()
            }
          )
        )
      },
      maxFormDataEntrySize = 1024 * 1024 * 20
    )
  }
}

case class State(formData: Option[FormData] = None,
                 error: Option[String] = None)

object State {
  val globalContext = Context[Future, State, Any]
}
