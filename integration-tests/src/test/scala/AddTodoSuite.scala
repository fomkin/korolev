import java.io.File
import java.net.URL

import com.google.common.collect.{ImmutableList, ImmutableMap}
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.{ChromeDriver, ChromeDriverService}
import org.openqa.selenium.remote.{DesiredCapabilities, RemoteWebDriver}
import org.scalatest.concurrent.Eventually
import org.scalatest.selenium.WebBrowser
import org.scalatest.{DoNotDiscover, FunSuite, Matchers}

import scala.concurrent.duration._

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
@DoNotDiscover
class AddTodoSuite extends FunSuite with Matchers with Eventually with WebBrowser {

  val caps = DesiredCapabilities.edge()
  caps.setCapability("tunnel-identifier", System.getenv("TRAVIS_JOB_NUMBER"))
  caps.setCapability("build", System.getenv("TRAVIS_BUILD_NUMBER"))
  caps.setCapability("platform", "Windows 10")
  caps.setCapability("version", "14.14393")

  implicit lazy val webDriver: WebDriver = {
    val username = System.getenv("SAUCE_USERNAME")
    val accessKey = System.getenv("SAUCE_ACCESS_KEY")
    val url = new URL(s"http://$username:$accessKey@ondemand.saucelabs.com:80/wd/hub")
    new RemoteWebDriver(url, caps)
//    new ChromeDriver(new ChromeDriverService(
//      new File("/Users/fomkin/Downloads/chromedriver"), 9515,
//      ImmutableList.of(), ImmutableMap.of()
//    ))
  }

  test("Todo should be added on 'Add todo' click") {
    val newTodoText = "Hello world"
    go to "http://localhost:8000/"
    pageTitle should be ("The Test App")
    textField(id("todo-input")).value = newTodoText
    click on id("todo-submit-button")
    eventually(timeout(3.seconds)) {
      val newTodoExists = name("todo-list-item")
        .findAllElements
        .exists(_.text == newTodoText)
      newTodoExists should be (true)
    }
  }
}
