package gp

import korolev.blazeServer._
import org.openqa.selenium.By
import slogging.{LoggerConfig, SLF4JLoggerFactory}
import tools._

import scala.collection.JavaConverters._
import scala.concurrent.duration._

object GuineaPigTests extends App {

  LoggerConfig.factory = SLF4JLoggerFactory()

  val server = {

    import korolev.execution.defaultExecutor

    val service = GuineaPigServer.service
    val config = BlazeServerConfig(
      port = 8000,
      doNotBlockCurrentThread = true
    )
    korolev.blazeServer.runServer(service, config)
  }

  println("GuineaPig server has been started")

  val appUrl = "http://localhost:8000"
  val result = scenario("Evil, inhuman experiment")(genericCaps)(
    step("Page should be open successfully") { wd =>
      // Open browser
      wd.get(appUrl + "/")
      assert(wd.getTitle == "The Test App")
      sleep(10.seconds)
    },
    step("Switch to second tab") { wd =>
      wd.findElement(By.id("tab2")).click()
      sleep(5.seconds)
      assert(wd.getCurrentUrl == s"$appUrl/tab2")
    },
    step("Click on first ToDo") { wd =>
      val firstToDoCheckBox = wd
        .findElements(By.className("todo_checkbox")).asScala
        .head
      firstToDoCheckBox.click()
      sleep(5.seconds)
      assert(
        firstToDoCheckBox
        .getAttribute("class")
        .contains("todo_checkbox__checked")
      )
    },
    step("Todo should be added after 'Add todo' click") { wd =>
      // Add new todo
      val newTodoText = "Hello world"
      wd.findElement(By.id("todo-input")).sendKeys(newTodoText)
      wd.findElement(By.id("todo-submit-button")).click()
      sleep(10.seconds)

      // Check new dod
      val newTodoExists = wd
        .findElements(By.className("todo")).asScala
        .last.getText == newTodoText

      // Verify conditions
      if (!newTodoExists) {
        fail("Added todo entry is not found in todos list")
      }
    }
  )

  server.close()
  println("GuineaPig server shutting down")

  if (result) System.exit(0)
  else System.exit(1)
}
