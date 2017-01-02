package korolev.blazeServer

import org.http4s.blaze.http.HttpService

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
abstract class KorolevBlazeServer(config: BlazeServerConfig = BlazeServerConfig.default) {
  def service: HttpService
  def main(args: Array[String]): Unit = {
    runServer(service, config)
  }
}
