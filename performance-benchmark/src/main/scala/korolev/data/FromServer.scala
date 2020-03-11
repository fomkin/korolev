package korolev.data

import akka.actor.typed.ActorRef
import korolev.internal.Frontend
import pushka.Ast

sealed trait FromServer

object FromServer {

  case class Procedure(procedure: Frontend.Procedure, args: List[Any]) extends FromServer

  object Procedure {

    def apply(code: Int, args: List[Any]): Procedure = {
      new Procedure(Frontend.Procedure(code).get, args)
    }

    def fromJson(ast: List[Ast]): Either[String, Procedure] = try {
      val Ast.Num(procedureId) :: argsAsts = ast
      val code = procedureId.toInt
      Frontend.Procedure(code) match {
        case Some(procedure) =>
          val args = argsAsts.collect {
            case Ast.Str(s) => s
            case Ast.Num(n) if n.contains(".") => n.toDouble
            case Ast.Num(n) => n.toInt
            case Ast.False => false
            case Ast.True => true
            case Ast.Null => null
          }
          Right(FromServer.Procedure(procedure, args))
        case None => Left(s"unknown procedure #$code")
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
