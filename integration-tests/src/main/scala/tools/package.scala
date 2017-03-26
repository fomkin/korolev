
import java.net.URL
import java.util.concurrent.Executors

import org.openqa.selenium.WebDriver
import org.openqa.selenium.remote.RemoteWebDriver
import org.slf4j.LoggerFactory

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
package object tools {

  val logger = LoggerFactory.getLogger("tools")

  case class Step(caption: String, lambda: WebDriver => Unit)

  def step(caption: String)(lambda: WebDriver => Unit) =
    Step(caption, lambda)

  def scenario(scenario: String)(cases: Seq[Caps])(
      steps: Step*): Boolean = {

    // Sauce labs give us 5 parallel sessions
    implicit val defaultExecutor = ExecutionContext.
      fromExecutorService(Executors.newWorkStealingPool(2))

    val username = System.getenv("SAUCE_USERNAME")
    val accessKey = System.getenv("SAUCE_ACCESS_KEY")

    val resultFutures = for (caps <- cases) yield {
      Future {

        val capsDc = caps.desiredCapabilities
        val scenarioName = s"'$scenario' for ${capsDc.getPlatform}/${capsDc.getBrowserName}"
        val sb = StringBuilder.newBuilder

        // Show message immediately
        println(s"! ${Console.BLUE}$scenarioName started.${Console.RESET}")
        // Header as part of report
        sb.append(s"${Console.BOLD}Report: $scenarioName${Console.RESET}\n")

        val webDriver = {
          val url = new URL(
            s"http://$username:$accessKey@ondemand.saucelabs.com:80/wd/hub")
          val desiredCapabilities = caps.desiredCapabilities
          desiredCapabilities.setCapability("tunnel-identifier", System.getenv("TRAVIS_JOB_NUMBER"))
          desiredCapabilities.setCapability("build", System.getenv("TRAVIS_BUILD_NUMBER"))
          new RemoteWebDriver(url, desiredCapabilities)
        }

        val sauceClient = new SauceLabsClient(username,
          accessKey,
          webDriver.getSessionId.toString)

        sauceClient.setName(scenario)

        def runScenariosRec(skip: Boolean, steps: Seq[Step]): Boolean =
          steps match {
            case Nil => !skip
            case step :: xs if skip =>
              sb.append(s"- ${step.caption} 😓\n")
              runScenariosRec(skip = true, xs)
            case step :: xs =>
              try {
                step.lambda(webDriver)
                sb.append(s"- ${step.caption} 😃\n")
                runScenariosRec(skip = false, xs)
              } catch {
                case e: Throwable =>
                  sb.append(s"- ${step.caption} 😡\n")
                  sb.append(Console.RED)
                  sb.append(s"  Cause: ${e.getMessage}")
                  for (st <- e.getStackTrace)
                    sb.append(s"    $st\n")
                  sb.append(Console.RESET)
                  runScenariosRec(skip = true, xs)
              }
          }

        val isPassed = runScenariosRec(skip = false, steps.toList)

        sauceClient.setPassed(isPassed)
        webDriver.quit()

        println(sb.mkString)
        isPassed
      } recover {
        case e: Throwable =>
          logger.error(s"In $scenario with ${caps.desiredCapabilities}", e)
          false
      }
    }

    val resultFuture = Future.sequence(resultFutures)
    Await.result(resultFuture.map(_.forall(identity)), 1 hour)
  }

  def fail(message: String): Unit = {
    throw new AssertionError(message)
  }

  def sleep(duration: FiniteDuration): Unit = {
    Thread.sleep(duration.toMillis)
  }
}
