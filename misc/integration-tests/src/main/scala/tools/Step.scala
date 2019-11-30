package tools

import org.openqa.selenium.WebDriver

case class Step(caption: String, lambda: WebDriver => StepResult)
