import korolev._
import korolev.server._
import korolev.blazeServer._
import korolev.execution._
import korolev.state.javaSerialization._

import scala.concurrent.Future

object EventDataExample extends KorolevBlazeServer {

  val globalContext = Context[Future, String, Any]

  import globalContext._
  import symbolDsl._

  val service = blazeService[Future, String, Any] from KorolevServiceConfig [Future, String, Any] (
    serverRouter = ServerRouter.empty[Future, String],
    stateStorage = StateStorage.default("nothing"),
    render = {
      case json =>
        'body(
          'input(
            'type /= "text",
            event('keydown) { access =>
              access.eventData.flatMap { eventData =>
                access.transition(_ => eventData)
              }
            }
          ),
          'pre(json)
        )
    }
  )
}

