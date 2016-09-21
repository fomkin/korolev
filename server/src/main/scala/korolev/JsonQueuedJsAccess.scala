package korolev

package vaska

import bridge.JSAccess

import scala.collection.immutable.Queue
import scala.concurrent.ExecutionContext

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
class JsonQueuedJsAccess(sendJson: String => Unit)(implicit val executionContext: ExecutionContext) extends JSAccess {

  @volatile var queue = Queue.empty[String]

  def seqToJSON(xs: Seq[Any]): String = {
    val xs2 =
      xs map {
        case s: String if !s.startsWith("[") ⇒ "\"" + s.replace("\n", "\\n") + "\""
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
    queue = queue.enqueue(message)
  }


  override def flush(): Unit = {
    val rawRequests = synchronized {
      val items = queue
      queue = Queue.empty
      items
    }
    val requests = rawRequests.mkString(",")
    sendJson(s"""["batch",$requests]""")
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
