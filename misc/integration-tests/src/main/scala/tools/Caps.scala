package tools

import org.openqa.selenium.remote.DesiredCapabilities

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
