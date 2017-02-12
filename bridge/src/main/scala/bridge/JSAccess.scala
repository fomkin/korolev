package bridge

import java.util.concurrent.atomic.AtomicInteger

import korolev.Async

import scala.collection.mutable
import scala.language.higherKinds
import scala.util.{Failure, Success}

object JSAccess {

  class JSSideException(message: String) extends Exception(message)

  final class JSSideErrorCode(val code: Int) extends JSSideException(s"Error code $code")

  final class JSSideUnrecognizedException() extends JSSideException("Unrecognized exception")

  val LinkPrefix = "@link:"
  val ArrayPrefix = "@arr:"
  val ObjPrefix = "@obj:"
  val UnitResult = "@unit"
  val NullResult = "@null"
}

/**
 * Provide access to remote page with JavaScript engine
 * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
 */
abstract class JSAccess[F[+_]: Async] { self ⇒

  import JSAccess._

  lazy val global = obj("global")

  case class Request(id: Int, args: Seq[Any], resultPromise: Async.Promise[F, Any])

  /**
   * Increment it after request
   */
  protected val lastReqId = new AtomicInteger(0)

  protected val lastCallbackId = new AtomicInteger(0)

  /**
   * List of promises of requests. Resolves by
   * income messages
   */
  protected def promises: mutable.Map[Int, Async.Promise[F, Any]]

  protected def callbacks: mutable.Map[String, Any ⇒ Unit]

  /**
   * Abstract method sends message to remote page
   */
  def send(args: Seq[Any]): Unit

  /**
   * Make request to remote page.
   *
   * @tparam A Type you need
   *
   * @return Future with result.
   *         It may be any basic type or [[JSLink]]
   */
  def request[A](args: Any*): F[A] = {
    val promise = Async[F].promise[Any]
    val requestId = lastReqId.getAndIncrement()
    promises.put(requestId, promise)

    sendRequest(Request(requestId, Seq(requestId) ++ args, promise))

    // Unpack result
    Async[F].map(promise.future)(unpackArg[A])
  }

  protected def sendRequest(request: Request): Unit = {
    sendRequest(request.args) { e ⇒
      request.resultPromise.complete(Failure(e))
    }
  }

  protected def sendRequest(args: Seq[Any])(exceptionHandler: (Throwable) ⇒ Unit): Unit = {
    try {
      send(packArgs(args))
    } catch {
      case exception: Throwable ⇒
        exceptionHandler(exception)
    }
  }

  def array(linkId: String): JSArray[F] = {
    new JSArray() {
      val jsAccess = self
      val id = linkId
    }
  }

  def obj(linkId: String): JSObj[F] = {
    new JSObj() {
      val jsAccess = self
      val id = linkId
    }
  }

  def platformDependentPack(value: Any): Any = value

  def platformDependentUnpack(value: Any): Any = value

  def packArgs(args: Seq[Any]): Seq[Any] = args collect {
    case anyLink: JSLink[F] ⇒ LinkPrefix + anyLink.id
    case xs: Seq[Any] ⇒ platformDependentPack(packArgs(xs))
    case hook: Hook ⇒ hook.requestString
    case otherwise ⇒ platformDependentPack(otherwise)
  }

  def unpackArg[A](arg: Any): A = arg match {
    case s: String if s.startsWith(ObjPrefix) ⇒
      val id = s.stripPrefix(ObjPrefix)
      obj(id).asInstanceOf[A]
    case s: String if s.startsWith(ArrayPrefix) ⇒
      val id = s.stripPrefix(ArrayPrefix)
      array(id).asInstanceOf[A]
    case `UnitResult` ⇒
      ().asInstanceOf[A]
    case `NullResult` ⇒
      null.asInstanceOf[A]
    case otherwise ⇒
      platformDependentUnpack(otherwise).
        asInstanceOf[A]
  }

  def resolvePromise(reqId: Int, isSuccess: Boolean, res: Any): Unit = {
    promises.get(reqId) match {
      case Some(promise) ⇒
        if (isSuccess) {
          promise.complete(Success(res))
        }
        else {
          val exception = res match {
            case s: String ⇒ new JSSideException(s)
            case i: Int ⇒ new JSSideErrorCode(i)
            case _ ⇒ new JSSideUnrecognizedException()
          }
          promise.complete(Failure(exception))
        }
        promises -= reqId
      case None ⇒
        println(s"Promise for $reqId not found")
    }
  }

  def fireCallback(callbackId: String, arg: Any): Unit = {
    callbacks(callbackId)(unpackArg(arg))
  }

  def registerCallback[T](f: T ⇒ Unit): F[JSObj[F]] = {
    val callbackId = s"^cb${lastCallbackId.getAndIncrement()}"
    callbacks.put(callbackId, f.asInstanceOf[Any ⇒ Unit])
    request("registerCallback", callbackId)
  }

  def registerCallbackAndFlush[T](f: T ⇒ Unit): F[JSObj[F]] = {
    val result = registerCallback(f)
    flush()
    result
  }


  def flush(): Unit = {
    // Do nothing by default
  }
}
