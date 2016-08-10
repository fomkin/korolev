package bridge

import scala.concurrent.Future
import scala.language.dynamics

/**
 * JavaScript Object presentation. 
 * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
 */
trait JSObj extends JSLink {

  def get[A](name: String): Future[A] = {
    jsAccess.request("get", this, name)
  }

  def set[A](name: String, value: A): Future[Unit] = {
    jsAccess.request("set", this, name, value)
  }

  def call[A](name: String, args: Any*): Future[A] = {
    val req = Seq("call", this, name) ++ args
    jsAccess.request(req: _*)
  }

  /**
   * Make call to object and save result with specified id 
   * @return
   */
  def callAndSaveAs(name: String, args: Any*)(id: String): Future[JSObj] = {
    val req = Seq("callAndSaveAs", this, name, id) ++ args
    jsAccess.request(req: _*)
  }
  
  def getAndSaveAs(name: String, id: String): Future[JSObj] = {
    jsAccess.request("getAndSaveAs", this, name, id)
  }
}
