import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO

import korolev._
import korolev.blazeServer._
import korolev.server._
import korolev.execution._

import scala.concurrent.Future

object FormDataExample extends KorolevBlazeServer {
  import State.effects._

  val myForm = elementId
  val pictureFieldName = "picture"

  val service = blazeService[Future, State, Any] from KorolevServiceConfig[Future, State, Any](
    stateStorage = StateStorage.default(State.empty),
    serverRouter = ServerRouter.empty,
    head = 'head(
      'link(
        'rel /="stylesheet",
        'href /="https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0-alpha.6/css/bootstrap.min.css",
        'integrity /= "sha384-rwoIResjU2yc3z8GV/NPeZWAv56rSmLldC3R/AZzGRnGxQQKnKkoFVhFQhNUwEyJ",
        'crossorigin /= "anonymous"
      ),
      'style("""
        body { margin: 2em }
      """),
      'script(
        'src /= "https://code.jquery.com/jquery-3.1.1.slim.min.js",
        'integrity /= "sha384-A7FZj7v+d/sdmMqp/nOQwliLvUsJfDHW+k9Omg/a/EheAdgtzNs3hpfag6Ed950n",
        'crossorigin /= "anonymous"
      ),
      'script(
        'src /= "https://cdnjs.cloudflare.com/ajax/libs/tether/1.4.0/js/tether.min.js",
        'integrity /= "sha384-DztdAPBWPRXSA/3eYEEUWrWCy7G5KFbe8fFjk5JAIxUYHKkDx6Qin1DkWx51bBrb",
        'crossorigin /= "anonymous"
      ),
      'script(
        'src /= "https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0-alpha.6/js/bootstrap.min.js",
        'integrity /= "sha384-vBWWzlZJ8ea9aCX4pEW3rVHjgjt7zpkNpZk+02D9phzyeVkE+jo0ieGizqPLForn",
        'crossorigin /= "anonymous"
      )
    ),
    render = {
      case Initial =>
        'body(
          'form('class /= "card",
            'div(
              'class /= "card-block",
              'legend("FormData Example"),
              'p(
                'label("Picture"),
                'input('type /= "file", 'name /= pictureFieldName)
              ),
              'p(
                'button("Submit")
              )
            ),
            myForm,
            eventWithAccess('submit) { access =>
              immediateTransition { case _ =>
                InProgress(0, 100)
              } deferredTransition {
                access
                  .downloadFormData(myForm)
                  .onProgress { (loaded, total) =>
                    transition {
                      case _ =>
                        InProgress(loaded, total)
                    }
                  }
                  .start()
                  .map { formData =>
                    transition { case _ =>
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
                  }
              }
            }
          )
        )
      case InProgress(loaded, total) =>
        'body(
          'div('class /= "card",
            'div('class /= "card-block",
              'div('class /= "progress",
                'div(
                  'class /= "progress-bar progress-bar-striped progress-bar-animated",
                  'role /= "progress-bar",
                  'style /= s"width: ${(loaded.toDouble/total) * 100}%"
                )
              )
            )
          )
        )
      case Complete(picture, mimeType, width, height) =>
        'body(
          'div(
            'style /=
               s"""background-image: url("data:$mimeType;base64,$picture");""" +
               s"width: ${width}px;" +
               s"height: ${height}px"
          )
        )
    }
  )
}

sealed trait State

case object Initial extends State
case class InProgress(loaded: Int, total: Int) extends State
case class Complete(picture: String, mimeType: String, width: Int, height: Int) extends State
case class Error(message: String) extends State

object State {
  def empty: State = Initial
  val effects = Effects[Future, State, Any]
}
