/*
 * Copyright 2017-2018 Aleksey Fomkin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package korolev.blazeServer

import java.net.InetAddress
import java.security.KeyStore
import javax.net.ssl.{KeyManagerFactory, SSLContext}

import org.http4s.blaze.util.BogusKeystore

import scala.concurrent.ExecutionContextExecutorService

/**
  * @param sslContext Standard Java SSL context. Use [[BlazeServerConfig#bogusSslContext]] for tests
  */
case class BlazeServerConfig(
  port: Int = 8080,
  host: String = InetAddress.getLoopbackAddress.getHostAddress,
  sslContext: Option[SSLContext] = None,
  bufferSize: Int = 8 * 1024,
  maxRequestBodySize: Int = 8 * 1024 * 1024,
  maxRequestHeaderSize: Int = 10 * 1024,
  doNotBlockCurrentThread: Boolean = false
)(
  // Trampoline
  implicit val executionContext: ExecutionContextExecutorService
)

object BlazeServerConfig {

  def bogusSslContext: SSLContext = {
    val ksStream = BogusKeystore.asInputStream()
    assert(ksStream != null)
    val ks = KeyStore.getInstance("JKS")
    ks.load(ksStream, BogusKeystore.getKeyStorePassword)
    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(ks, BogusKeystore.getCertificatePassword)
    val context = SSLContext.getInstance("SSL")
    context.init(kmf.getKeyManagers, null, null)
    context
  }
}
