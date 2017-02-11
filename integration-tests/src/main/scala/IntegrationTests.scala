import korolev.blazeServer._
import org.openqa.selenium.By
import org.openqa.selenium.remote.DesiredCapabilities
import tools._

import scala.concurrent.duration._
import scala.collection.JavaConverters._

object IntegrationTests extends App {

  val server = {
    val service = GuineaPig.service
    val config = BlazeServerConfig(
      port = 8000,
      doNotBlockCurrentThread = true
    )
    korolev.blazeServer.runServer(service, config)
  }

  val genericCaps =  Seq(
//    Caps(DesiredCapabilities.android _)(
//      "deviceName" ->  "Android Emulator" ,
//      "deviceOrientation" ->   "portrait" ,
//      "browserName" ->   "Browser" ,
//      "platformVersion" ->   "4.4" ,
//      "platformName" ->  "Android"
//    ),
    Caps(DesiredCapabilities.chrome _)(
      "platform" -> "Windows 7",
      "version" -> "55.0",
      "chromedriverVersion" -> "2.27"
    ),
    Caps(DesiredCapabilities.edge _)(
      "platform" -> "Windows 10",
      "version" -> "14.14393"
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
    )
//    Caps(DesiredCapabilities.iphone _)(
//      "appiumVersion" -> "1.5.3",
//      "deviceName" -> "iPhone 6 Device",
//      "deviceOrientation" -> "portrait",
//      "platformVersion" -> "8.4",
//      "platformName" -> "iOS",
//      "browserName" -> "Safari"
//    ),
//    Caps(DesiredCapabilities.iphone _)(
//      "appiumVersion" -> "1.5.3",
//      "deviceName" -> "iPhone 6 Device",
//      "deviceOrientation" -> "portrait",
//      "platformVersion" -> "9.3",
//      "platformName" -> "iOS",
//      "browserName" -> "Safari"
//    )
  )

  scenario("Add ToDo item")(genericCaps)(
    step("Todo should be added on 'Add todo' click") { wd =>
      // Open browser
      wd.get("http://localhost:8000/")
      assert(wd.getTitle == "The Test App")
      sleep(3.seconds)

      // Add new todo
      val newTodoText = "Hello world"
      wd.findElement(By.id("todo-input")).sendKeys(newTodoText)
      wd.findElement(By.id("todo-submit-button")).click()
      sleep(1.second)

      // Check new dod
      val newTodoExists = wd.findElements(By.name("todo-list-item")).asScala exists { element =>
        element.getText == newTodoText
      }

      // Verify conditions
      if (!newTodoExists) {
        fail("Added todo entry is not found in todos list")
      }
    }
  )

  server.close()
}
