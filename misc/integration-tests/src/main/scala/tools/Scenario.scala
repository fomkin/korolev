package tools

import java.net.URL
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import org.openqa.selenium.remote.RemoteWebDriver

import scala.concurrent.{ExecutionContext, Future}

case class Scenario(name: String, steps: Seq[Step])(implicit actorSystem: ActorSystem) {

  def run(cases: Caps*): Future[Boolean] = {
    // Sauce labs give us 5 parallel sessions
    implicit val defaultExecutor = ExecutionContext.fromExecutorService(Executors.newWorkStealingPool(5))

    val username = System.getenv("SAUCE_USERNAME")
    val accessKey = System.getenv("SAUCE_ACCESS_KEY")

    val resultFutures = for (caps <- cases) yield {
      Future {

        val capsDc = caps.desiredCapabilities
        val scenarioName = s"'$name' for ${capsDc.getPlatform}/${capsDc.getBrowserName}/${capsDc.getVersion}"
        val sb = new StringBuilder()

        // Show message immediately
        println(s"! ${Console.BLUE}$scenarioName started.${Console.RESET}")
        // Header as part of report
        sb.append(s"${Console.BOLD}Report: $scenarioName${Console.RESET}\n")

        val webDriver = {
          val url = new URL(s"http://$username:$accessKey@ondemand.saucelabs.com:80/wd/hub")
          val desiredCapabilities = caps.desiredCapabilities
          desiredCapabilities.setCapability("tunnel-identifier", System.getenv("TRAVIS_JOB_NUMBER"))
          desiredCapabilities.setCapability("build", System.getenv("TRAVIS_BUILD_NUMBER"))
          new RemoteWebDriver(url, desiredCapabilities)
        }

        val sauceClient = new SauceLabsClient(username, accessKey, webDriver.getSessionId.toString)

        sauceClient.setName(name)

        def runScenariosRec(skip: Boolean, steps: Seq[Step]): Boolean =
          steps match {
            case Nil => !skip
            case step :: xs if skip =>
              sb.append(s"- ${step.caption} 😓\n")
              runScenariosRec(skip = true, xs)
            case step :: xs =>
              val result = try {
                step.lambda(webDriver)
              } catch {
                case e: Throwable => StepResult.Error(e)
              }
              result match {
                case StepResult.CowardlySkipped(reason) =>
                  sb.append(s"- ${step.caption} ($reason) 🤐\n")
                case StepResult.Ok =>
                  sb.append(s"- ${step.caption} 😃\n")
                case StepResult.Error(e) =>
                  sb.append(s"- ${step.caption} 😡\n")
                  sb.append(Console.RED)
                  sb.append(s"  Cause: ${e.getMessage}")
                  for (st <- e.getStackTrace)
                    sb.append(s"    $st\n")
                  sb.append(Console.RESET)
              }
              runScenariosRec(skip = false, xs)
          }

        val isPassed = runScenariosRec(skip = false, steps.toList)

        sauceClient.setPassed(isPassed)
        webDriver.quit()

        println(sb.mkString)
        isPassed
      } recover {
        case e: Throwable =>
          logger.error(s"In $name with ${caps.desiredCapabilities}", e)
          false
      }
    }

    Future
      .sequence(resultFutures)
      .map(_.reduce(_ && _))
  }
}
