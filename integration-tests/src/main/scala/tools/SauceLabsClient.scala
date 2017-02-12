package tools

import java.nio.ByteBuffer
import java.util.Base64
import javax.net.ssl.SSLContext

import org.http4s.blaze.channel.nio2.ClientChannelFactory
import org.http4s.blaze.http.{HttpClient, HttpResponse}
import org.http4s.blaze.util.{Execution, GenericSSLContext}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
class SauceLabsClient(userName: String, accessKey: String, jobId: String) extends HttpClient {

  import scala.concurrent.ExecutionContext.Implicits.global

  override lazy val connectionManager = new ClientChannelFactory()
  override protected val sslContext: SSLContext = GenericSSLContext.clientSSLContext()

  def setName(name: String): Unit = {
    putToJob(s"""{"name": "$name"}""")
  }

  def setPassed(passed: Boolean): Unit = {
    putToJob(s"""{"passed": $passed}""")
  }

  def putToJob(data: String): Unit = {
    val authorization = {
      val s = s"$userName:$accessKey"
      Base64.getEncoder.encodeToString(s.getBytes)
    }
    val headers = Seq(
      "content-type" -> "application/json",
      "authorization" -> s"Basic $authorization"
    )
    val response = PUT(
      url = s"https://saucelabs.com/rest/v1/$userName/jobs/$jobId", headers,
      body = ByteBuffer.wrap(data.getBytes),
      5 seconds
    )
    Await.result(response, 10 seconds)
  }

  // Cause blaze client doesn't support anything else GET!
  def PUT(url: String, headers: Seq[(String, String)], body: ByteBuffer,timeout: Duration): Future[HttpResponse] = {
    val r = runReq("PUT", url, headers, body, timeout)
    r.flatMap {
      case r: HttpResponse => Future.successful(r)
      case r => Future.failed(new Exception(s"Received invalid response type: ${r.getClass}"))
    }(Execution.directec)
  }
}
