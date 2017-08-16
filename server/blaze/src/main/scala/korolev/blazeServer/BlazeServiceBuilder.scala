package korolev.blazeServer

import korolev.Async
import korolev.server.{KorolevServiceConfig, MimeTypes}
import korolev.util.Scheduler
import org.http4s.blaze.http.HttpService
import scala.reflect.runtime.universe.TypeTag

final class BlazeServiceBuilder[F[+_]: Async, S: TypeTag, M](mimeTypes: MimeTypes) {
  def from(config: KorolevServiceConfig[F, S, M])(implicit scheduler: Scheduler[F]): HttpService =
    blazeService(config, mimeTypes)
}
