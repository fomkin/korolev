import pushka.Ast
import Ast._
import pushka.json._


/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
sealed trait KorolevIncomingMessage

object KorolevIncomingMessage {

  case class FunctionCall(id: Int, ref: String, fun: String, args: List[Ast]) extends KorolevIncomingMessage
  case class GetAndSaveAs(id: Int, ref: String, prop: String, savedRef: String) extends KorolevIncomingMessage
  case class RegisterCallback(id: Int, ref: String) extends KorolevIncomingMessage

  def decodeKorolevMessage(parts: List[Ast]): List[KorolevIncomingMessage] = parts match {
    case Ast.Str("batch") :: xs => xs flatMap {
      case Ast.Arr(message) => decodeKorolevMessage(message.toList)
      case json => throw new Exception(s"Expected json array but $json was given")
    }
    case Ast.Num(id) :: Ast.Str("call") :: Ast.Str(ref) :: Ast.Str(fun) :: args =>
      FunctionCall(id.toInt, ref, fun, args) :: Nil
    case Ast.Num(id) :: Ast.Str("getAndSaveAs") :: Ast.Str(ref) :: Ast.Str(prop) :: Ast.Str(savedRef) :: Nil =>
      GetAndSaveAs(id.toInt, ref, prop, savedRef)  :: Nil
    case Ast.Num(id) :: Ast.Str("registerCallback") :: Ast.Str(ref) :: Nil =>
      RegisterCallback(id.toInt, ref) :: Nil
    case json =>
      throw new Exception(s"Unsupported message: $json")
  }

  def decodeKorolevMessage(s: String): List[KorolevIncomingMessage] = {
    val Ast.Arr(iterable) = read[Ast](s)
    decodeKorolevMessage(iterable.toList)
  }

  def encodeKorolevMessage(xs: Any*): String = {
    def convert(value: Any): Ast = value match {
      case x: Ast ⇒ x
      case x: Byte ⇒ Num(x.toString)
      case x: Short ⇒ Num(x.toString)
      case x: Int ⇒ Num(x.toString)
      case x: Float ⇒ Num(x.toString)
      case x: Double ⇒ Num(x.toString)
      case x: Long ⇒ Num(x.toString)
      case x: Boolean if x ⇒ True
      case x: Boolean if !x ⇒ False
      case x: String ⇒ Str(x)
      case x: Seq[_] ⇒ Arr(x.map(convert).toList)
    }
    write[Ast](Ast.Arr(xs.map(convert)))
  }
}
