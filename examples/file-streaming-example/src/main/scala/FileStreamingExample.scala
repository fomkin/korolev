import java.io.{File, FileOutputStream}

import korolev._
import korolev.akkahttp._
import korolev.execution._
import korolev.server._
import korolev.state.javaSerialization._

import scala.concurrent.Future

object FileStreamingExample extends SimpleAkkaHttpKorolevApp {

  case class State(progress: Map[String, (Long, Long)], inProgress: Boolean)

  val globalContext = Context[Future, State, Any]

  import globalContext._
  import symbolDsl._

  val fileInput = elementId()

  def onUploadClick(access: Access) = {
    access.downloadFilesAsStream(fileInput).flatMap { files =>
      access.transition(_.copy(files.map(x => (x.name, (0L, x.data.size.getOrElse(0L)))).toMap, inProgress = true)).flatMap { _ =>
        Future.sequence {
          files.map { file =>
            val size = file.data.size.getOrElse(0L)
            def loop(acc: Long): Future[Unit] = file.data.pull() flatMap {
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
      router = Router.empty,
      stateStorage = StateStorage.default(State(Map.empty, inProgress = false)),
      render = {
        case State(progress, inProgress) =>
          'body(
            'input('type /= "file", 'multiple /= "multiple", fileInput),
            'ul(
              progress.map {
                case (name, (loaded, total)) =>
                  'li(s"$name: $loaded / $total")
              }
            ),
            'button(
              "Upload",
              if (inProgress) 'disabled /= "" else void,
              event('click)(onUploadClick)
            )
          )
      }
    )
  }
}

