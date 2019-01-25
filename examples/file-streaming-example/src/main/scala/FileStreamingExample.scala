import java.io.{File, FileOutputStream}

import korolev._
import korolev.akkahttp._
import korolev.execution._
import korolev.server._
import korolev.state.javaSerialization._

import scala.concurrent.Future

object FileStreamingExample extends SimpleAkkaHttpKorolevApp {

  case class State(loaded: Int, total: Long, inProgress: Boolean)

  val globalContext = Context[Future, State, Any]

  import globalContext._
  import symbolDsl._

  val fileInput = elementId()

  val file = new File("file-streaming-example.dump")

  val service = akkaHttpService {
    KorolevServiceConfig[Future, State, Any] (
      router = emptyRouter,
      stateStorage = StateStorage.default(State(0, 0, inProgress = false)),
      render = {
        case State(loaded, total, inProgress) =>
          'body(
            'input('type /= "file", fileInput),
            'button(
              if (inProgress) s"$loaded/$total"
              else "Upload",
              event('click) { access =>
                access.downloadFileAsStream(fileInput).flatMap { lazyBytes =>
                  access.transition(_.copy(total = lazyBytes.size.getOrElse(0L), inProgress = true)).flatMap { _ =>
                    def loop(): Future[Unit] = lazyBytes.pull() flatMap {
                      case Some(bytes) =>
                        val w = new FileOutputStream(file, true)
                        w.write(bytes)
                        w.flush()
                        w.close()
                        access.transition(x => x.copy(loaded = x.loaded + bytes.length)).flatMap(_ => loop())
                      case None =>
                        Future.unit
                    }
                    loop()
                  }
                }
              }
            )
          )
      }
    )
  }
}

