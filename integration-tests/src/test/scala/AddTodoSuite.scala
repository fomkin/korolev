import java.io.File
import java.net.URL

import com.google.common.collect.{ImmutableList, ImmutableMap}
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.{ChromeDriver, ChromeDriverService}
import org.openqa.selenium.remote.{DesiredCapabilities, RemoteWebDriver}
import org.scalatest.concurrent.Eventually
import org.scalatest.selenium.WebBrowser
import org.scalatest.{BeforeAndAfter, DoNotDiscover, FunSuite, Matchers}

import scala.concurrent.duration._

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
@DoNotDiscover
class AddTodoSuite(caps: Caps) extends FunSuite with Matchers with BeforeAndAfter with Eventually with WebBrowser {

  implicit lazy val webDriver: WebDriver = {
    val username = System.getenv("SAUCE_USERNAME")
    val accessKey = System.getenv("SAUCE_ACCESS_KEY")
    val url = new URL(s"http://$username:$accessKey@ondemand.saucelabs.com:80/wd/hub")
    new RemoteWebDriver(url, caps.desiredCapabilities)
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

  after {
    webDriver.quit()
  }
}
