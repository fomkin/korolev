import java.io.File

import akka.actor.ActorSystem
import akka.typed.scaladsl.adapter._
import data.{Report, Scenario, ToServer}
import scala.concurrent.ExecutionContext.Implicits.global

object PerformanceBenchmark extends App {

  def setup(scenario: Scenario) = {
    import akka.typed.scaladsl.Actor
    Actor.deferred[ToServer] { ctx =>
      KorolevConnection("localhost", 8080, None, ssl = false) {
        ctx.spawnAnonymous {
          ScenarioExecutor(scenario) {
            ctx.spawnAnonymous {
              Actor.immutable[Report] { (_, message) =>
                println(message)
                Actor.same
              }
            }
          }
        }
      }
    }
  }

  args match {
    case Array(fileName) =>
      val file = new File(fileName)
      if (file.isDirectory) {
        println(s"$fileName is a directory")
        sys.exit(1)
      }
      if (!file.exists()) {
        println("Given file doesn't exist")
        sys.exit(1)
      } else {
        val system = ActorSystem("performance-benchmark")
        ScenarioLoader.fromFile(file) foreach {
          case Left(errors) =>
            system.terminate()
            errors.foreach(println)
          case Right(scenario) =>
            system.spawn(setup(scenario), "connection")
        }
      }
    case _ =>
      println("No file given")
      sys.exit(1)
  }
}
