import java.io.ByteArrayInputStream
import java.util.Base64

import javax.imageio.ImageIO
import korolev._
import korolev.akkahttp.{AkkaHttpServerConfig, SimpleAkkaHttpKorolevApp}
import korolev.akkahttp._
import korolev.server._
import korolev.execution._
import korolev.state.javaSerialization._
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
      stateLoader = StateLoader.default(State.empty),
      head = _ => {
        Seq(
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
        )
      },
      render = { state =>

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
                label ("Picture"),
                input (`type` := "file", name := pictureFieldName)
              ),
              p (
                button ("Submit")
              )
            ),
            event("submit") { access =>
              for {
                formData <- access
                  .downloadFormData(myForm)
                  .onProgress((loaded, total) => _ => InProgress(loaded, total))
                  .start()
                _ <- access.resetForm(myForm)
                _ = println(formData)
                _ <- access.transition { _ =>
                  val buffer = formData.bytes(pictureFieldName)
                  val pictureBase64 = Base64.getEncoder.encodeToString(buffer.array())
                  val parsedImage = ImageIO.read(new ByteArrayInputStream(buffer.array()))

                  formData.contentType(pictureFieldName) match {
                    case Some(mimeType) =>
                      Complete(
                        picture = pictureBase64,
                        mimeType = mimeType,
                        width = parsedImage.getWidth,
                        height = parsedImage.getHeight
                      )
                    case None =>
                      Error("Unknown image format")
                  }
                }
              } yield ()
            }
          ),
          state match {
            case Initial => div()
            case InProgress(loaded, total) =>
              div (`class` := "card",
                div (`class` := "card-block",
                  div (`class` := "progress",
                    div (
                      `class` := "progress-bar progress-bar-striped progress-bar-animated",
                      role := "progress-bar",
                      width @= s"${(loaded.toDouble / total) * 100}%"
                    )
                  )
                )
              )
            case Complete(picture, mimeType, w, h) =>
              div (
                backgroundImage @= s"url(data:$mimeType;base64,$picture')",
                width @= s"${w}px",
                height @= s"${h}px"
              )
            case Error(msg) =>
              div(backgroundColor @= "red", color @= "white", msg)
          }
        )
      },
      maxFormDataEntrySize = 1024 * 1024 * 20
    )
  }
}

sealed trait State

case object Initial extends State
case class InProgress(loaded: Int, total: Int) extends State
case class Complete(picture: String, mimeType: String, width: Int, height: Int) extends State
case class Error(message: String) extends State

object State {
  def empty: State = Initial
  val globalContext = Context[Future, State, Any]
}
