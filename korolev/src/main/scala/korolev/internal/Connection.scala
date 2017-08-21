package korolev.internal

import java.util.concurrent.ConcurrentLinkedQueue

import korolev.Async
import korolev.Async._

import scala.annotation.switch
import scala.util.Success

final class Connection[F[+ _] : Async] {

  import Connection._

  private val incoming = new Channel[F, String]()
  private val outgoing = new Channel[F, String]()

  def received: F[String] = incoming.read

  def sent: F[String] = outgoing.read

  def receive(message: String): Unit = incoming.write(message)

  def send(args: Any*): Unit = {

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
      ()
    }

    val sb = StringBuilder.newBuilder
    sb.append('[')
    args.foreach {
      case s: String =>
        escape(sb, s, unicode = true)
        sb.append(',')
      case x =>
        sb.append(x.toString)
        sb.append(',')
    }
    sb.update(sb.length - 1, ' ') // replace last comma to space
    sb.append(']')

    outgoing.write(sb.mkString)
  }
}

object Connection {

  /** Channel with only one consumer */
  final class Channel[F[+ _] : Async, T] {

    private val queue = new ConcurrentLinkedQueue[T]()
    private var promise = Option.empty[Promise[F, T]]

    def write(message: T): Unit = this.synchronized {
      promise match {
        case Some(p) =>
          promise = None
          p.complete(Success(message))
        case None =>
          queue.add(message)
          ()
      }
    }

    def read: F[T] = this.synchronized {
      if (queue.isEmpty) {
        promise match {
          case Some(p) =>
            p.future
          case None =>
            val p = Async[F].promise[T]
            promise = Some(p)
            p.future
        }
      }
      else {
        val message = queue.poll()
        Async[F].pure(message)
      }
    }
  }

}
