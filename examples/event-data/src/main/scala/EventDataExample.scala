import korolev._
import korolev.server._
import korolev.akka._
import korolev.state.javaSerialization._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object EventDataExample extends SimpleAkkaHttpKorolevApp {

  val globalContext = Context[Future, String, Any]

  import globalContext._
  import levsha.dsl._
  import html._

  val service = akkaHttpService {
    KorolevServiceConfig [Future, String, Any] (
      stateLoader = StateLoader.default("nothing"),
      document = json => optimize {
        Html(
          body(
            input(
              `type` := "text",
              event("keydown") { access =>
                access.eventData.flatMap { eventData =>
                  access.transition(_ => eventData)
                }
              }
            ),
            pre(json)
          )
        )
      }
    )
  }
}

