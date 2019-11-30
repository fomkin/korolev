package korolev

import java.io.File

import akka.actor.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import com.typesafe.config.ConfigFactory
import korolev.data.{FromServer, Report, Scenario, ToServer}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object PerformanceBenchmark extends App {

  def setup(scenario: Scenario, conf: BenchmarkConfiguration)(implicit untypedSystem: ActorSystem): Behavior[ToServer] = {

    Behaviors.setup[ToServer] { ctx =>
      val reporter = ctx.spawnAnonymous {
        Behaviors.receiveMessagePartial[Report] {
          case Report.Unexpected(state, expected, gotten) =>
            println(s"${state.scenario.name}:${state.step + 1}: $expected expected, but $gotten gotten")
            Behaviors.same

          case Report.Success(_, metrics) =>
            val ts = metrics.values.toVector
            val avg = (ts.sum / ts.length) / 1000000d
            val mean = ts.sorted.apply(ts.length / 2) / 1000000d
            val min = ts.min / 1000000d
            val max = ts.max / 1000000d
            println(s"avg: $avg, mean: $mean in [$min, $max]")
            Behaviors.same

          case Report.MessagesFromClosedConnection(message) =>
            message match {
              case FromServer.ErrorOccurred(korolev.data.Error.ArbitraryThrowable(e)) =>
                print(Console.RED)
                println(s"${e.getMessage}, ${e.getCause}")
                e.getStackTrace.foreach { s =>
                  print(Console.RED)
                  println("  " + s)
                }
              case _ => println(message)
            }
            Behaviors.same

          case message =>
            println(message)
            Behaviors.same
        }
      }

      def behavior(i: Int) = KorolevConnection(conf.host, conf.port, Some(conf.path), conf.ssl) {
        ctx.spawn(ScenarioExecutor(scenario, reporter, Some(1.seconds)), s"executor-$i")
      }

      for (i <- 1 to conf.testers) {
        ctx.spawn(behavior(i), s"bench-$i")
      }

      Behaviors.ignore
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
        val config = ConfigFactory.load()

        val benchmarkConfig = BenchmarkConfiguration(
          config.getString("benchmark.host"),
          config.getInt("benchmark.port"),
          config.getString("benchmark.path"),
          config.getBoolean("benchmark.ssl"),
          config.getInt("benchmark.testers")
        )

        ScenarioLoader.fromFile(file) foreach {
          case Left(errors) =>
            system.terminate()
            errors.foreach(println)
          case Right(scenario) =>
            system.spawn(setup(scenario, benchmarkConfig)(system), "connection")
        }
      }
    case _ =>
      println("No file given")
      sys.exit(1)
  }
}