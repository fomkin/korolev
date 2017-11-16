package data

import akka.typed.ActorRef
import korolev.internal.ClientSideApi

sealed trait FromServer

object FromServer {
  case class Procedure(procedure: ClientSideApi.Procedure, args: List[Any]) extends FromServer
  object Procedure {
    def apply(code: Int, args: List[Any]): Procedure = {
      new Procedure(ClientSideApi.Procedure(code).get, args)
    }
  }
  case class ErrorOccurred(error: Error) extends FromServer
  case class Connected(ref: ActorRef[ToServer]) extends FromServer
  case object Closed extends FromServer
}
