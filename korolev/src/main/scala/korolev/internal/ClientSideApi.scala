package korolev.internal

import bridge.JSAccess
import korolev.Async
import korolev.Async._
import korolev.Router.Path
import levsha.Id
import levsha.impl.DiffRenderContext.ChangesPerformer

/**
  * Typed interface to client side
  */
final class ClientSideApi[F[+ _]: Async](jsAccess: JSAccess[F]) extends ChangesPerformer {

  private val async = Async[F]

  private val client = {
    jsAccess.global.getAndSaveAs("Korolev", "@Korolev").runIgnoreResult
    jsAccess.obj("@Korolev")
  }

  private def isProp(name: String) = name.charAt(0) == '^'

  private def escapeName(name: String, isProp: Boolean) =
    if (isProp) name.substring(1)
    else name

  private def registerCallback(apiFunctionName: String, f: String => Unit) = {
    jsAccess.registerCallbackAndFlush[String](f).flatMap { callback =>
      client.callAndFlush[Unit](apiFunctionName, callback)
    }
  }

  def initialize(
      doCleanRoot: Boolean,
      doReloadCss: Boolean,
      onHistory: String => Unit,
      onEvent: String => Unit,
      onFormDataProgress: String => Unit
  ): F[Unit] =
    async sequence {
      Seq(
        registerCallback("RegisterHistoryHandler", onHistory),
        registerCallback("RegisterGlobalEventHandler", onEvent),
        registerCallback("RegisterFormDataProgressHandler", onFormDataProgress),
        setRenderNum(0, doFlush = true),
        if (doCleanRoot) client.callAndFlush("CleanRoot") else async.unit,
        if (doReloadCss) client.callAndFlush("ReloadCss") else async.unit
      )
    } map { _ =>
      ()
    }

  def listenEvent(name: String, preventDefault: Boolean): F[Unit] =
    client.callAndFlush("ListenEvent", name, preventDefault)

  def uploadForm(id: Id, descriptor: String): F[Unit] =
    client.callAndFlush("UploadForm", id.mkString, descriptor)

  def focus(id: Id): F[Unit] =
    client.callAndFlush("Focus", id.mkString)

  def setAttr[T](id: Id, xmlNs: String, name: String, value: T, isProperty: Boolean): F[Unit] =
    client.callAndFlush("SetAttr", id.mkString, xmlNs, name, value, isProperty)

  def extractProperty[T](id: Id, name: String): F[T] =
    client.callAndFlush("ExtractProperty", id.mkString, name)

  def changePageUrl(path: Path): F[Unit] = {
    client.call[Unit]("ChangePageUrl", path.toString)
  }

  def setRenderNum(i: Int, doFlush: Boolean): F[Unit] = {
    val future = client.call[Unit]("SetRenderNum", i)
    if (doFlush) jsAccess.flush()
    future
  }

  def remove(id: Id): Unit =
    client.call("Remove", id.parent.get.mkString, id.mkString).runIgnoreResult()

  def createText(id: Id, text: String): Unit =
    client.call("CreateText", id.parent.get.mkString, id.mkString, text).runIgnoreResult()

  def create(id: Id, xmlNs: String, tag: String): Unit = {
    val parent = id.parent.fold("0")(_.mkString)
    val pXmlns =
      if (xmlNs eq levsha.XmlNs.html.uri) 0
      else xmlNs
    client.call("Create", parent, id.mkString, pXmlns, tag).runIgnoreResult()
  }

  def setAttr(id: Id, xmlNs: String, name: String, value: String): Unit = {
    val p = isProp(name)
    val n = escapeName(name, p)
    val pXmlns =
      if (xmlNs eq levsha.XmlNs.html.uri) 0
      else xmlNs
    client.call("SetAttr", id.mkString, pXmlns, n, value, p).runIgnoreResult()
  }

  def removeAttr(id: Id, xmlNs: String, name: String): Unit = {
    val p = isProp(name)
    val n = escapeName(name, p)
    val pXmlns =
      if (xmlNs eq levsha.XmlNs.html.uri) 0
      else xmlNs
    client.call("RemoveAttr", id.mkString, pXmlns, n, p).runIgnoreResult()
  }
}
