package korolev.server

import java.util.concurrent.ConcurrentLinkedQueue

import bridge.JSAccess
import korolev.Async
import korolev.Async.Promise

import scala.annotation.switch
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.language.higherKinds
/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
case class JsonQueuedJsAccess[F[+_]: Async](sendJson: String => Unit) extends JSAccess[F] {

  protected val promises = TrieMap.empty[Int, Promise[F, Any]]
  protected val callbacks = TrieMap.empty[String, (Any) => Unit]
  val queue = new ConcurrentLinkedQueue[String]()

  def escape(sb: StringBuilder, s: String, unicode: Boolean): Unit = {
    sb.append('"')
    var i = 0
    val len = s.length
    while (i < len) {
      (s.charAt(i): @switch) match {
        case '"' => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\b' => sb.append("\\b")
        case '\f' => sb.append("\\f")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case c =>
          if (c < ' ' || (c > '~' && unicode)) sb.append("\\u%04x" format c.toInt)
          else sb.append(c)
      }
      i += 1
    }
    sb.append('"')
  }

  def seqToJSON(xs: Seq[Any]): String = {
    val xs2 =
      xs map {
        case s: String if !s.startsWith("[") ⇒
          val sb = new StringBuilder
          escape(sb, s, unicode = true)
          sb.mkString
        case any ⇒ any
      }
    "[" + xs2.reduce(_ + ", " + _) + "]"
  }

  override def platformDependentPack(value: Any): Any = value match {
    case xs: Seq[Any] ⇒ seqToJSON(xs)
    case x ⇒ super.platformDependentPack(x)
  }

  /**
    * Abstract method sends message to remote page
    */
  def send(args: Seq[Any]): Unit = {
    val message = seqToJSON(args)
    queue.add(message)
  }


  override def flush(): Unit = {
    val buffer = mutable.Buffer.empty[String]
    while (!queue.isEmpty) {
      buffer += queue.poll()
    }
    if (buffer.size == 1) {
      sendJson(buffer.head)
    }
    else if (buffer.nonEmpty) {
      val requests = buffer.mkString(",")
      sendJson(s"""["batch",$requests]""")
    }
  }

  def receive(message: String): Unit = {
    def prepareString(value: String) = {
      value match {
        case s: String if s.startsWith("\"") ⇒
          s.substring(1, s.length - 1).trim
        case s ⇒ s.trim
      }
    }
    val args =
      message.stripPrefix("[").stripSuffix("]").split(",").map(prepareString)
    val reqId = args(0).toInt
    if (reqId == -1) {
      val callbackId = args(1)
      val arg = args(2)
      fireCallback(callbackId, arg)
    } else {
      val isSuccess = args(1).toBoolean
      val res = args(2)
      resolvePromise(reqId, isSuccess, res)
    }
  }
}
