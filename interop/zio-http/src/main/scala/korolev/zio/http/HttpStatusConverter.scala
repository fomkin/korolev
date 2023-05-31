package korolev.zio.http

import korolev.web.Response.{Status => KStatus}
import zio.http.Status

object HttpStatusConverter {

  def fromKorolevStatus(kStatus: KStatus): Status =
    Status.fromInt(kStatus.code).orNull
}
