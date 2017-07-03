package korolev.akkahttp.util

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, Props, Status}
import akka.stream.scaladsl.Sink

class IncomingMessageHandler(publish: String => Unit,
                             destroyHandler: () => Unit)
                            (implicit actorSystem: ActorSystem) {

  val asSink: Sink[String, NotUsed] =
    Sink.actorRef(incomingMessageHandlerActor, ConnectionClosed)

  private class IncomingMessageHandlerActor extends Actor {
    override def receive: Receive = {
      case message: String =>
        publish(message)

      case ConnectionClosed =>
        destroyHandler()

      case Status.Failure =>
        self ! ConnectionClosed
    }
  }

  private lazy val incomingMessageHandlerActor: ActorRef =
    actorSystem.actorOf(Props(new IncomingMessageHandlerActor))

  private object ConnectionClosed

}
