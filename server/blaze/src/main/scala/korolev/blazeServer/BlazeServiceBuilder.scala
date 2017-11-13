package korolev.blazeServer

import korolev.Async
import korolev.execution.Scheduler
import korolev.server.{KorolevServiceConfig, MimeTypes}
import korolev.state.{StateDeserializer, StateSerializer}
import org.http4s.blaze.http.HttpService

final class BlazeServiceBuilder[F[+_]: Async: Scheduler, S: StateSerializer: StateDeserializer, M](mimeTypes: MimeTypes) {
  def from(config: KorolevServiceConfig[F, S, M]): HttpService =
    blazeService(config, mimeTypes)
}
