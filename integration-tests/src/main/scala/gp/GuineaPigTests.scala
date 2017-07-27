package gp

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import korolev.execution._
import korolev.blazeServer._
import korolev.akkahttp._
import org.openqa.selenium.By
import org.openqa.selenium.remote.DesiredCapabilities
import slogging.{LoggerConfig, SLF4JLoggerFactory}
import tools._

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration._

object GuineaPigTests extends App {

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

  val runScenario = () => scenario("Evil, inhuman experiment")(genericCaps)(
    step("Page should be open successfully") { wd =>
      // Open browser
      wd.get(appUrl + "/")
      assert(wd.getTitle == "The Test App")
      sleep(10.seconds)
    },
    step("Switch to second tab") { wd =>
      wd.findElement(By.id("tab2")).click()
      sleep(5.seconds)
      assert(wd.getCurrentUrl == s"$appUrl/tab2")
    },
    step("Click on first ToDo") { wd =>
      val firstToDoCheckBox = wd
        .findElements(By.className("todo_checkbox")).asScala
        .head
      firstToDoCheckBox.click()
      sleep(5.seconds)
      assert(
        firstToDoCheckBox
        .getAttribute("class")
        .contains("todo_checkbox__checked")
      )
    },
    step("Todo should be added after 'Add todo' click") { implicit wd =>
      // Add new todo
      val newTodoText = "Hello world"
      val input = wd.findElement(By.id("todo-input"))
      input.scrollTo()
      input.sendKeys(newTodoText)
      wd.findElement(By.id("todo-submit-button")).click()
      sleep(10.seconds)

      // Check new dod
      val newTodoExists = wd
        .findElements(By.className("todo")).asScala
        .last.getText == newTodoText

      // Verify conditions
      if (!newTodoExists) {
        fail("Added todo entry is not found in todos list")
      }
    },
    step("Field should be empty after todo was added") { wd =>
      val value = wd.findElement(By.id("todo-input")).getAttribute("value")
      assert(value == "", "Field should be empty")
    }
  )

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
