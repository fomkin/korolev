package data

import korolev.internal.ClientSideApi
import pushka.Ast

sealed trait ToServer

object ToServer {

  case object Close extends ToServer
  case class Callback(tpe: ClientSideApi.CallbackType, data: String) extends ToServer

  object Callback {

    def apply(code: Int, data: String): Callback = {
      new Callback(ClientSideApi.CallbackType(code).get, data)
    }

    def fromJson(ast: List[Ast]): Either[String, Callback] = {
      val Ast.Num(codeString) :: Ast.Str(data) :: Nil = ast
      val code = codeString.toInt
      ClientSideApi.CallbackType(code) match {
        case None => Left(s"unknown callback #$code")
        case Some(callback) => Right(Callback(callback, data))
      }
    }
  }
}
