import akka.actor.ActorSystem

package object gp {

  implicit val actorSystem: ActorSystem = ActorSystem()

}
