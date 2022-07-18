package korolev.zio.http

import io.netty.handler.codec.http.HttpResponseStatus
import korolev.web.Response.{Status => KStatus}
import zhttp.http.Status

object HttpStatusConverter {

  def fromKorolevStatus(kStatus: KStatus): Status =
    Status.fromHttpResponseStatus(HttpResponseStatus.valueOf(kStatus.code))

}
