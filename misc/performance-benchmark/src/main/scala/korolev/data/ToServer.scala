package korolev.data

import korolev.internal.Frontend.CallbackType
import ujson._

sealed trait ToServer

object ToServer {

  case object Close extends ToServer

  case class Callback(tpe: CallbackType, data: Option[String]) extends ToServer

  object Callback {

    def apply(code: Int, data: String): Callback = {
      new Callback(CallbackType(code).get, Some(data))
    }

    def fromJson(ast: Value): Either[String, Callback] = {
      ast match {
        case arr: Arr =>
          arr.value.toList match {
            case Num(codeString) :: Nil =>
              val code = codeString.toInt
              CallbackType(code) match {
                case None => Left(s"unknown callback #$code")
                case Some(callback) => Right(Callback(callback, None))
              }
            case Num(codeString) :: Str(data) :: Nil =>
              val code = codeString.toInt
              CallbackType(code) match {
                case None => Left(s"unknown callback #$code")
                case Some(callback) => Right(Callback(callback, Some(data)))
              }
          }
        case other =>
          Left(s"Unexpected JSON #$other")
      }
    }
  }

}