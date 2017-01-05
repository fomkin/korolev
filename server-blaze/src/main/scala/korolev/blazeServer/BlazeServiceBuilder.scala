package korolev.blazeServer

import korolev.Async
import korolev.server.{KorolevServiceConfig, MimeTypes}
import org.http4s.blaze.http.HttpService

import scala.language.higherKinds

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
final class BlazeServiceBuilder[F[+_]: Async, S, M](mimeTypes: MimeTypes) {
  def from(config: KorolevServiceConfig[F, S, M]): HttpService =
    blazeService(config, mimeTypes)
}
