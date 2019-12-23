import java.nio.file.Paths

import korolev._
import korolev.akka._
import korolev.effect.io.FileIO
import korolev.execution._
import korolev.server._
import korolev.state.javaSerialization._

import scala.concurrent.Future
import scala.concurrent.duration._

object FileStreamingExample extends SimpleAkkaHttpKorolevApp {

  case class State(aliveIndicator: Boolean, progress: Map[String, (Long, Long)], inProgress: Boolean)

  val globalContext = Context[Future, State, Any]

  import globalContext._
  import levsha.dsl._
  import html._

  val fileInput = elementId()

  def onUploadClick(access: Access) = {

    def showProgress(fileName: String, loaded: Long, total: Long) = access
      .transition { state =>
        val updated = state.progress + ((fileName, (loaded, total)))
        state.copy(progress = updated)
      }

    for {
      files <- access.downloadFilesAsStream(fileInput)
      _ <- access.transition(_.copy(progress = files.map(x => (x.name, (0L, x.data.bytesLength.getOrElse(0L)))).toMap, inProgress = true))
      _ <- Future.sequence {
        files.map { file =>
          val size = file.data.bytesLength.getOrElse(0L)
          // File will be saved in 'downloads' directory
          // in the root of the example project
          val path = Paths.get("downloads", file.name)
          file.data.chunks
            .over(0L) {
              case (acc, chunk) =>
                val loaded = chunk.fold(acc)(_.length.toLong + acc)
                showProgress(file.name, loaded, size)
                  .map(_ => loaded)
            }
            .to(FileIO.write(path))
        }
      }
      _ <- access.transition(_.copy(inProgress = false))
    } yield ()
  }

  val service = akkaHttpService {
    KorolevServiceConfig[Future, State, Any] (
      stateLoader = StateLoader.default(State(aliveIndicator = false, Map.empty, inProgress = false)),
      render = {
        case State(aliveIndicator, progress, inProgress) => optimize {
          body(
            delay(1.second) { access => access.transition(_.copy(aliveIndicator = !aliveIndicator)) },
            div(when(aliveIndicator)(backgroundColor @= "red"), "Online"),
            input(`type` := "file", multiple, fileInput),
            ul(
              progress.map {
                case (name, (loaded, total)) =>
                  li(s"$name: $loaded / $total")
              }
            ),
            button(
              "Upload",
              when(inProgress)(disabled),
              event("click")(onUploadClick)
            )
          )
        }
      }
    )
  }
}

