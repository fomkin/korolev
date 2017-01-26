import java.io.File
import java.net.URL

import com.google.common.collect.{ImmutableList, ImmutableMap}
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.{ChromeDriver, ChromeDriverService}
import org.openqa.selenium.logging.LogType
import org.openqa.selenium.remote.{DesiredCapabilities, RemoteWebDriver}
import org.scalatest.concurrent.Eventually
import org.scalatest.selenium.WebBrowser
import org.scalatest.{BeforeAndAfter, DoNotDiscover, FunSuite, Matchers}
import scala.collection.JavaConverters._
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
  }

  test(s"$caps: Todo should be added on 'Add todo' click") {
    val newTodoText = "Hello world"
    go to "http://localhost:8000/"
    pageTitle should be ("The Test App")
    textField(id("todo-input")).value = newTodoText
    click on id("todo-submit-button")
    Thread.sleep(1000)
    val newTodoExists = name("todo-list-item")
      .findAllElements.toSeq
      .exists(_.text == newTodoText)

    if (!newTodoExists) {
      printBrowserLog()
      fail("Added todo entry is not found in todos list")
    }
    newTodoExists should be (true)
  }

  def printBrowserLog(): Unit = {
//    val log = webDriver.manage().logs().get(LogType.BROWSER)
//    println("Browser log:")
//    for (logEntry <- log.asScala)
//      println(s"- [${logEntry.getLevel}] ${logEntry.getMessage}")
  }

  after {
    webDriver.quit()
  }
}
