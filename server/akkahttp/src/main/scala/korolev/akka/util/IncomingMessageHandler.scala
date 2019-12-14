/*
 * Copyright 2017-2018 Aleksey Fomkin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package korolev.akka.util

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
