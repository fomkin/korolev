import java.io.{File, FileOutputStream}

import korolev._
import korolev.akka._
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

  val dir = new File("downloads")
  dir.mkdirs()

  def onUploadClick(access: Access) = {
    for {
      files <- access.downloadFilesAsStream(fileInput)
      _ <- access.transition(_.copy(progress = files.map(x => (x.name, (0L, x.data.bytesLength.getOrElse(0L)))).toMap, inProgress = true))
      _ <- Future.sequence {
        files.map { file =>
          val size = file.data.bytesLength.getOrElse(0L)
          //val w = new FileOutputStream(new File(dir, file.name), true)
          //val w = new FileOutputStream(new File("/dev/null"))
          def loop(acc: Long): Future[Unit] = file.data.chunks.pull() flatMap {
            case Some(bytes) =>
              val loaded = acc + bytes.length
              //w.write(bytes)
              access
                .transition { state =>
                  val updated = state.progress + ((file.name, (loaded, size)))
                  state.copy(progress = updated)
                }
                .flatMap(_ => loop(loaded))
//              loop(loaded)
            case None =>
//              w.flush()
//              w.close()
              Future.unit
          }
          loop(0L)
        }
      }.recover {
        case e =>
          e.printStackTrace()
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

