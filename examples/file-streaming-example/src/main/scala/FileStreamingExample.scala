import java.io.{File, FileOutputStream}

import korolev._
import korolev.akka._
import korolev.execution._
import korolev.server._
import korolev.state.javaSerialization._

import scala.concurrent.Future

object FileStreamingExample extends SimpleAkkaHttpKorolevApp {

  case class State(progress: Map[String, (Long, Long)], inProgress: Boolean)

  val globalContext = Context[Future, State, Any]

  import globalContext._
  import levsha.dsl._
  import html._

  val fileInput = elementId()

  def onUploadClick(access: Access) = {
    access.downloadFilesAsStream(fileInput).flatMap { files =>
      access.transition(_.copy(files.map(x => (x.name, (0L, x.data.bytesLength.getOrElse(0L)))).toMap, inProgress = true)).flatMap { _ =>
        Future.sequence {
          files.map { file =>
            val size = file.data.bytesLength.getOrElse(0L)
            def loop(acc: Long): Future[Unit] = file.data.chunks.pull() flatMap {
              case Some(bytes) =>
                val loaded = acc + bytes.length
                val w = new FileOutputStream(new File(file.name), true)
                w.write(bytes)
                w.flush()
                w.close()
                access
                  .transition { state =>
                    val updated = state.progress + ((file.name, (loaded, size)))
                    state.copy(progress = updated)
                  }
                  .flatMap(_ => loop(loaded))
              case None =>
                Future.successful(())
            }
            loop(0L)
          }
        }.flatMap { _ =>
          access.transition(_.copy(inProgress = false))
        }
      }
    }
  }

  val service = akkaHttpService {
    KorolevServiceConfig[Future, State, Any] (
      stateLoader = StateLoader.default(State(Map.empty, inProgress = false)),
      render = {
        case State(progress, inProgress) => optimize {
          body(
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

