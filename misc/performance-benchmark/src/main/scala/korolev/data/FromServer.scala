package korolev.data

import akka.actor.typed.ActorRef
import korolev.internal.Frontend
import ujson._

sealed trait FromServer

object FromServer {

  case class Procedure(procedure: Frontend.Procedure, args: List[Any]) extends FromServer

  object Procedure {

    def apply(code: Int, args: List[Any]): Procedure = {
      new Procedure(Frontend.Procedure(code).get, args)
    }

    def fromJson(ast: Value): Either[String, Procedure] =
      try {
        ast match {
          case arr: Arr =>
            val Num(procedureId) :: argsAsts = arr.value.toList
            val code = procedureId.toInt

            Frontend.Procedure(code) match {
              case Some(procedure) =>
                val args = argsAsts.collect {
                  case Str(s) => s
                  case Num(n) if n.toString.contains(".") => n.toDouble
                  case Num(n) => n.toInt
                  case False => false
                  case True => true
                  case Null => null
                }
                Right(FromServer.Procedure(procedure, args))
              case None => Left(s"unknown procedure #$code")
            }
          case other =>
            Left(s"Unexpected JSON #$other")
        }
      } catch {
        case e: MatchError =>
          Left(s"can't parse ast $e")
      }
  }

  case class ErrorOccurred(error: Error) extends FromServer

  case class Connected(ref: ActorRef[ToServer]) extends FromServer

  case object Closed extends FromServer

}
