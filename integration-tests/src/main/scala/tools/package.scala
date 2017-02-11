
import java.net.URL

import org.openqa.selenium.WebDriver
import org.openqa.selenium.remote.RemoteWebDriver

import scala.concurrent.duration.FiniteDuration

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
package object tools {

  case class Step(caption: String, lambda: WebDriver => Unit)

  def step(caption: String)(lambda: WebDriver => Unit) =
    Step(caption, lambda)

  def scenario(scenario: String)(cases: Seq[Caps])(
      steps: Step*): Unit = {

    val username = System.getenv("SAUCE_USERNAME")
    val accessKey = System.getenv("SAUCE_ACCESS_KEY")

    for (caps <- cases) {

      println(s"-------------------------------")
      println(s"Testing in ${caps.desiredCapabilities}:")
      println()

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
            println(s" - [_] ${step.caption}")
            runScenariosRec(skip = true, xs)
          case step :: xs =>
            try {
              step.lambda(webDriver)
              println(s" - [✅] ${step.caption}")
              runScenariosRec(skip = false, xs)
            } catch {
              case e: Throwable =>
                println(s" - [❌] ${step.caption}")
                e.printStackTrace()
                runScenariosRec(skip = true, xs)
            }
        }

      println(s"$scenario:")

      val isPassed = runScenariosRec(skip = false, steps.toList)

      try {
        sauceClient.setPassed(isPassed)
        webDriver.quit()
      } catch {
        case _: Throwable =>
          // do nothing
      }
    }
  }

  def fail(message: String): Unit = {
    throw new AssertionError(message)
  }

  def sleep(duration: FiniteDuration): Unit = {
    Thread.sleep(duration.toMillis)
  }
}
