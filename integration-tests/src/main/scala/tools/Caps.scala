package tools

import java.util.logging.Level

import org.openqa.selenium.logging.{LogType, LoggingPreferences}
import org.openqa.selenium.remote.{CapabilityType, DesiredCapabilities}

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
case class Caps(desiredCapabilities: DesiredCapabilities)

object Caps {

  def apply(builder: () => DesiredCapabilities)(capabilities: (String, Any)*): Caps = {
    val caps = builder()
    capabilities foreach {
      case (k, v) =>
        caps.setCapability(k, v)
    }
    new Caps(caps)
  }

}
