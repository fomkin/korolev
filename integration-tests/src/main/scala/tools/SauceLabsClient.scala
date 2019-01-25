package tools

import java.util.Base64

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._

import scala.concurrent.Await
import scala.concurrent.duration._

class SauceLabsClient(userName: String, accessKey: String, jobId: String)(implicit actorSystem: ActorSystem) {

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

    val request = HttpRequest(
      uri = s"https://saucelabs.com/rest/v1/$userName/jobs/$jobId",
      method = HttpMethods.PUT,
      headers = List(Authorization(BasicHttpCredentials(authorization))),
      entity = HttpEntity(ContentTypes.`application/json`, data.getBytes)
    )

    val response = Http()
      .singleRequest(request)

    Await.result(response, 10 seconds)
    ()
  }
}
