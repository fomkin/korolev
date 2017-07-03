package korolev.akkahttp.util

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.Source

class OutgoingMessageWriter(implicit actorSystem: ActorSystem) {

  val asSource: Source[Message, NotUsed] =
    Source.fromPublisher(ActorPublisher[Message](responderActor))

  def write(message: String): Unit = {
    responderActor ! TextMessage(message)
  }

  def close(): Unit = {
    responderActor ! QueuingActorPublisher.Finish
  }

  private class ResponderActor extends QueuingActorPublisher[Message]

  private lazy val responderActor: ActorRef = actorSystem.actorOf(Props(new ResponderActor))

}
