import org.openqa.selenium.remote.DesiredCapabilities
import tools.Caps

package object gp {
  val genericCaps = Seq(
    Caps(DesiredCapabilities.chrome _)(
      "platform" -> "Windows 7",
      "version" -> "55.0",
      "chromedriverVersion" -> "2.27"
    ),
    Caps(DesiredCapabilities.edge _)(
      "platform" -> "Windows 10",
      "version" -> "14.14393"
    ),
    Caps(DesiredCapabilities.internetExplorer _)(
      "platform" -> "Windows 7",
      "version" -> "9.0"
    ),
    Caps(DesiredCapabilities.internetExplorer _)(
      "platform" -> "Windows 7",
      "version" -> "11"
    ),
    Caps(DesiredCapabilities.firefox _)(
      "platform" -> "Linux",
      "version" -> "45.0",
      "seleniumVersion" -> "2.53.0"
    ),
    Caps(DesiredCapabilities.android _)(
      "deviceName" ->  "Android Emulator" ,
      "deviceOrientation" ->   "portrait" ,
      "browserName" ->   "Browser" ,
      "platformVersion" -> "5.1" ,
      "platformName" ->  "Android"
    ),
    Caps(DesiredCapabilities.safari _)(
      "platform" -> "OS X 10.11",
      "version" -> "10.0"
    ),
    Caps(DesiredCapabilities.iphone _)(
      "appiumVersion" -> "1.5.3",
      "deviceName" -> "iPhone 6 Simulator",
      "deviceOrientation" -> "portrait",
      "platformVersion" -> "8.4",
      "platformName" -> "iOS",
      "browserName" -> "Safari",
      "autoAcceptAlerts" -> "true"
    ),
    Caps(DesiredCapabilities.iphone _)(
      "appiumVersion" -> "1.5.3",
      "deviceName" -> "iPhone 6 Simulator",
      "deviceOrientation" -> "portrait",
      "platformVersion" -> "9.3",
      "platformName" -> "iOS",
      "browserName" -> "Safari",
      "autoAcceptAlerts" -> "true"
    )
  )
}
