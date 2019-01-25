import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

package object gp {

  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()

}
