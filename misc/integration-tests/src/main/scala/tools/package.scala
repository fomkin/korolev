
import akka.actor.ActorSystem
import org.openqa.selenium.{JavascriptExecutor, WebDriver, WebElement}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

package object tools {

  val logger = LoggerFactory.getLogger("tools")

  def step(caption: String)(lambda: WebDriver => StepResult) =
    Step(caption, lambda)

  def scenario(name: String)(steps: Step*)(implicit as: ActorSystem): Scenario =
    Scenario(name, steps)

  def assert(message: String, f: => Boolean) = {
    if (!f) {
      val exception = new AssertionError(message)
      throw exception
    } else {
      StepResult.Ok
    }
  }

  def fail(message: String): Unit = {
    throw new AssertionError(message)
  }

  def sleep(duration: FiniteDuration): Unit = {
    Thread.sleep(duration.toMillis)
  }

  implicit final class WebElementOps(val el: WebElement) extends AnyVal {
    def scrollTo()(implicit webDriver: WebDriver): Unit = webDriver match {
      case executor: JavascriptExecutor =>
        executor.executeScript("arguments[0].scrollIntoView(true);", el)
        ()
      case _ =>
        throw new UnsupportedOperationException("WebDriver is not javascript executor")
    }
  }
}
