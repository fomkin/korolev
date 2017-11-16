package data

import korolev.internal.ClientSideApi

sealed trait ToServer

object ToServer {
  case object Close extends ToServer
  case class Callback(tpe: ClientSideApi.CallbackType, data: String) extends ToServer
  object Callback {
    def apply(code: Int, data: String): Callback = {
      new Callback(ClientSideApi.CallbackType(code).get, data)
    }
  }
}
