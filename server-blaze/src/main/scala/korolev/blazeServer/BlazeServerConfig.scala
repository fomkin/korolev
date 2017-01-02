package korolev.blazeServer

import java.net.InetAddress

import scala.concurrent.ExecutionContextExecutorService

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
case class BlazeServerConfig(
  port: Int = 8181,
  host: String = InetAddress.getLoopbackAddress.getHostAddress,
  bufferSize: Int = 8 * 1024
)(
  // Trampoline
  implicit val executionContext: ExecutionContextExecutorService
)

object BlazeServerConfig {
  val default = BlazeServerConfig()
}
