package gp

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import korolev.execution._
import korolev.blazeServer._
import korolev.akkahttp._
import org.openqa.selenium.remote.DesiredCapabilities
import slogging.{LoggerConfig, SLF4JLoggerFactory}
import tools._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

object GuineaPigRunner extends App {

  val genericCaps = Seq(
    Caps(DesiredCapabilities.chrome _)(
      "platform" -> "Windows 7",
      "version" -> "55.0",
      "chromedriverVersion" -> "2.27"
    ),
    Caps(DesiredCapabilities.edge _)(
      "platform" -> "Windows 10",
      "version" -> "14.14393"
    ),
    Caps(DesiredCapabilities.internetExplorer _)(
      "platform" -> "Windows 7",
      "version" -> "10.0"
    ),
    Caps(DesiredCapabilities.internetExplorer _)(
      "platform" -> "Windows 7",
      "version" -> "11"
    ),
    Caps(DesiredCapabilities.firefox _)(
      "platform" -> "Linux",
      "version" -> "45.0",
      "seleniumVersion" -> "2.53.0"
    ),
    Caps(DesiredCapabilities.android _)(
      "deviceName" ->  "Android Emulator" ,
      "deviceOrientation" ->   "portrait" ,
      "browserName" ->   "Browser" ,
      "platformVersion" -> "5.1" ,
      "platformName" ->  "Android"
    ),
    Caps(DesiredCapabilities.safari _)(
      "platform" -> "OS X 10.11",
      "version" -> "10.0"
    ),
    Caps(DesiredCapabilities.iphone _)(
      "appiumVersion" -> "1.5.3",
      "deviceName" -> "iPhone 6 Simulator",
      "deviceOrientation" -> "portrait",
      "platformVersion" -> "8.4",
      "platformName" -> "iOS",
      "browserName" -> "Safari",
      "autoAcceptAlerts" -> "true"
    ),
    Caps(DesiredCapabilities.iphone _)(
      "appiumVersion" -> "1.5.3",
      "deviceName" -> "iPhone 6 Simulator",
      "deviceOrientation" -> "portrait",
      "platformVersion" -> "9.3",
      "platformName" -> "iOS",
      "browserName" -> "Safari",
      "autoAcceptAlerts" -> "true"
    )
  )

  LoggerConfig.factory = SLF4JLoggerFactory()

  val servers = List(
    (scenario: () => Boolean) => {
      println("Starting Blaze server")
      blazeService.from(GuineaPigService.service)
      val service = blazeService.from(GuineaPigService.service)
      val config = BlazeServerConfig(port = 8000, doNotBlockCurrentThread = true)
      val server = korolev.blazeServer.runServer(service, config)
      Future {
        val result = scenario()
        server.close()
        println("Blaze server shutting down")
        result
      }
    },
    (scenario: () => Boolean) => {
      println("Starting Akka-http server")
      implicit val actorSystem = ActorSystem()
      implicit val materializer = ActorMaterializer()
      val route = akkaHttpService(GuineaPigService.service).apply(AkkaHttpServerConfig())
      Http().bindAndHandle(route, "localhost", 8000).map { server =>
        val result = scenario()
        println("Akka-http server shutting down")
        server.unbind()
        result
      }
    }
  )

  val appUrl = "http://localhost:8000"

  val runScenario = () => {
    val resultFutures = GuineaPigScenarios
      .all
      .map(_.run(genericCaps:_*))
    val resultFuture = Future
      .sequence[Boolean, Seq](resultFutures)
      .map(_.forall(identity))
    Await.result(resultFuture, 1.hour)
  }


  def runSerial(futures: List[(() => Boolean) => Future[Boolean]]): Future[Boolean] = futures match {
    case Nil => Future.successful(true)
    case x :: xs => x(runScenario).flatMap { result =>
      if (result) runSerial(xs)
      else Future.successful(false)
    }
  }

  runSerial(servers) foreach { result =>
    if (result) System.exit(0)
    else System.exit(1)
  }
}
