package korolev.blazeServer

import java.net.InetAddress
import java.security.KeyStore
import javax.net.ssl.{KeyManagerFactory, SSLContext}

import org.http4s.blaze.util.BogusKeystore

import scala.concurrent.ExecutionContextExecutorService

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
case class BlazeServerConfig(
  port: Int = 8181,
  host: String = InetAddress.getLoopbackAddress.getHostAddress,
  /**
    * Standard Java SSL context.
    * Use [[BlazeServerConfig.bogusSslContext]] for tests
    */
  sslContext: Option[SSLContext] = None,
  bufferSize: Int = 8 * 1024,
  doNotBlockCurrentThread: Boolean = false
)(
  // Trampoline
  implicit val executionContext: ExecutionContextExecutorService
)

object BlazeServerConfig {

  val default = BlazeServerConfig()

  def bogusSslContext: SSLContext = {
    val ksStream = BogusKeystore.asInputStream()
    assert(ksStream != null)
    val ks = KeyStore.getInstance("JKS")
    ks.load(ksStream, BogusKeystore.getKeyStorePassword)
    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    kmf.init(ks, BogusKeystore.getCertificatePassword)
    val context = SSLContext.getInstance("SSL")
    context.init(kmf.getKeyManagers, null, null)
    context
  }
}
