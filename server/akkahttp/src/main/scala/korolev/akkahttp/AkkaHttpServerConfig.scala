package korolev.akkahttp

case class AkkaHttpServerConfig(maxRequestBodySize: Int = AkkaHttpServerConfig.DefaultMaxRequestBodySize)

object AkkaHttpServerConfig {
  val DefaultMaxRequestBodySize: Int = 8 * 1024 * 1024
}
