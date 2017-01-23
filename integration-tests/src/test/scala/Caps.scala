import org.openqa.selenium.remote.DesiredCapabilities

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
case class Caps(
  browser: Caps.Browser,
  version: String,
  platform: String
) {
  lazy val desiredCapabilities: DesiredCapabilities = {
    val caps = browser.capsBuilder()
    caps.setCapability("tunnel-identifier", System.getenv("TRAVIS_JOB_NUMBER"))
    caps.setCapability("build", System.getenv("TRAVIS_BUILD_NUMBER"))
    caps.setCapability("platform", platform)
    caps.setCapability("version", version)
    caps
  }
}

object Caps {
  sealed abstract class Browser(val capsBuilder: () => DesiredCapabilities)
  case object Chrome extends Browser(DesiredCapabilities.chrome _)
  case object Edge extends Browser(DesiredCapabilities.edge _)
}
