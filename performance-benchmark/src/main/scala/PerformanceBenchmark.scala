import java.io.File

import akka.actor.ActorSystem
import akka.typed.scaladsl.adapter._
import data.{Report, Scenario, ToServer}
import scala.concurrent.ExecutionContext.Implicits.global

object PerformanceBenchmark extends App {

  def setup(scenario: Scenario) = {
    import akka.typed.scaladsl.Actor
    Actor.deferred[ToServer] { ctx =>
      val reporter = ctx.spawnAnonymous {
        Actor.immutable[Report] {
          case (_, Report.Unexpected(state, expected, gotten)) =>
            println(s"${state.scenario.name}:${state.step + 1}: $expected expected, but $gotten gotten")
            Actor.same
          case (_, Report.Success(_, metrics)) =>
            val ts = metrics.values.toVector
            val avg = (ts.sum / ts.length) / 1000000d
            val mean = ts.sorted.apply(ts.length / 2) / 1000000d
            val min = ts.min / 1000000d
            val max = ts.max / 1000000d
            println(s"avg: $avg, mean: $mean in [$min, $max]")
            Actor.same
          case (_, message) =>
            println(message)
            Actor.same
        }
      }
      def behavior(i: Int) = KorolevConnection("localhost", 8080, None, ssl = false) {
        ctx.spawn(ScenarioExecutor(scenario)(reporter), s"executor-$i")
      }
      for (i <- 1 to 512) {
        ctx.spawn(behavior(i), s"bench-$i")
      }
      Actor.ignore
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
