import java.util.logging.Level

import org.openqa.selenium.logging.{LogType, LoggingPreferences}
import org.openqa.selenium.remote.{CapabilityType, DesiredCapabilities}

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
    caps.setCapability("avoidProxy", true)
    // Enable logs
    val logPrefs = new LoggingPreferences()
    logPrefs.enable(LogType.BROWSER, Level.FINEST)
    caps.setCapability(CapabilityType.LOGGING_PREFS, logPrefs)
    caps
  }

  override def toString: String = {
    s"$platform/$browser/$version"
  }
}

object Caps {
  sealed abstract class Browser(val capsBuilder: () => DesiredCapabilities)
  case object Chrome extends Browser(DesiredCapabilities.chrome _)
  case object Edge extends Browser(DesiredCapabilities.edge _)
}
