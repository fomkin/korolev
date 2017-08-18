package korolev.blazeServer

import korolev.Async
import korolev.execution.Scheduler
import korolev.server.{KorolevServiceConfig, MimeTypes}
import org.http4s.blaze.http.HttpService

final class BlazeServiceBuilder[F[+_]: Async: Scheduler, S, M](mimeTypes: MimeTypes) {
  def from(config: KorolevServiceConfig[F, S, M]): HttpService =
    blazeService(config, mimeTypes)
}
