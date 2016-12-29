package bridge

import scala.language.higherKinds
import korolev.Async

/**
 * Link to entity on page side. By default, all links 
 * will be removed by GC cause its have no references in a page. 
 * PageLink give you ability to `save()` it. When you don't need
 * the link no more you can `free()` it.
 * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
 */
abstract class JSLink[F[+_]: Async] {

  val jsAccess: JSAccess[F]

  val id: String

  /**
   * Tell page to save reference to the link to avoid
   * garbage collection 
   */
  def save(): F[Unit] = {
    jsAccess.request("save", this, id)
  }

  /**
   * Tell page to save reference to the link with new id
   */
  def saveAs(newId: String): F[Unit] = {
    jsAccess.request("save", this, newId)
  }

  /**
   * Tell page you don't need the link no more.
   */
  def free(): F[Unit] = {
    jsAccess.request("free", this, id)
  }
}
