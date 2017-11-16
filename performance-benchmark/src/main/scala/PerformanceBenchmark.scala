import akka.actor.ActorSystem
import akka.typed.scaladsl.adapter._

object PerformanceBenchmark extends App {

  val guardian = {
    import akka.typed.scaladsl.Actor

    Actor.deferred[KorolevConnection.ToServer] { ctx =>
      KorolevConnection("localhost", 8080, None, ssl = false) {
        ctx.spawnAnonymous {
          Actor.immutable[KorolevConnection.FromServer] { (ctx, message) =>
            message match {
              case procedure: KorolevConnection.FromServer.Procedure =>
                println(procedure)
                Actor.same
              case KorolevConnection.FromServer.Connected =>
                println("connected")
                Actor.same
              case KorolevConnection.FromServer.Closed =>
                println("closed")
                Actor.same
              case KorolevConnection.FromServer.ErrorOccurred(error) =>
                println(s"error: $error")
                Actor.same
            }
          }
        }
      }
    }
  }

  val system = ActorSystem()
  system.spawn(guardian, "connection")
}
