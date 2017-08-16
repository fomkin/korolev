package korolev.blazeServer

import korolev.Async
import korolev.server.{KorolevServiceConfig, MimeTypes}
import korolev.util.Scheduler
import org.http4s.blaze.http.HttpService

final class BlazeServiceBuilder[F[+_]: Async, S, M](mimeTypes: MimeTypes) {
  def from(config: KorolevServiceConfig[F, S, M])(implicit scheduler: Scheduler[F]): HttpService =
    blazeService(config, mimeTypes)
}
