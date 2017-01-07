package bridge

import scala.language.higherKinds
import korolev.Async

/**
 * JavaScript Object presentation. 
 * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
 */
abstract class JSObj[F[+_]: Async] extends JSLink {

  def get[A](name: String): F[A] = {
    jsAccess.request("get", this, name)
  }

  def set[A](name: String, value: A): F[Unit] = {
    jsAccess.request("set", this, name, value)
  }

  def call[A](name: String, args: Any*): F[A] = {
    val req = Seq("call", this, name) ++ args
    jsAccess.request[A](req: _*)
  }

  def callAndFlush[A](name: String, args: Any*): F[A] = {
    val result = call(name, args:_*)
    jsAccess.flush()
    result
  }

  /**
   * Make call to object and save result with specified id 
   * @return
   */
  def callAndSaveAs(name: String, args: Any*)(id: String): F[JSObj[F]] = {
    val req = Seq("callAndSaveAs", this, name, id) ++ args
    jsAccess.request(req: _*)
  }
  
  def getAndSaveAs(name: String, id: String): F[JSObj[F]] = {
    jsAccess.request("getAndSaveAs", this, name, id)
  }
}
