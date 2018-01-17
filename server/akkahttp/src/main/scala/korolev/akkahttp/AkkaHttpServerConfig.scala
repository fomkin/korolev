package korolev.akkahttp

case class AkkaHttpServerConfig(maxRequestBodySize: Int = AkkaHttpServerConfig.DefaultMaxRequestBodySize,
                                outputBufferSize: Int = AkkaHttpServerConfig.DefaultOutputBufferSize)

object AkkaHttpServerConfig {
  val DefaultMaxRequestBodySize: Int = 8 * 1024 * 1024
  val DefaultOutputBufferSize: Int = 1000
}
