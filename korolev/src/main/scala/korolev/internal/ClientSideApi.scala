package korolev.internal

import korolev.{Async, Router}
import korolev.Async._
import korolev.Router.Path
import levsha.Id
import levsha.impl.DiffRenderContext.ChangesPerformer
import slogging.LazyLogging

import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.util.{Failure, Random, Success}

/**
  * Typed interface to client side
  */
final class ClientSideApi[F[+ _]: Async](connection: Connection[F])
  extends ChangesPerformer with LazyLogging {

  import ClientSideApi._

  private val async = Async[F]
  private val promises = TrieMap.empty[String, Promise[F, String]]
  private val domChangesBuffer = mutable.ArrayBuffer.empty[Any]

  private var onHistory: HistoryCallback = _
  private var onFormDataProgress: FormDataProgressCallback = _
  private var onEvent: EventCallback = _

  private def isProperty(name: String) =
    name.charAt(0) == '^'

  private def isStyle(name: String) =
    name.charAt(0) == '*'

  private def escapeName(name: String, truncate: Boolean) =
    if (truncate) name.substring(1)
    else name

  /**
    * @param onEvent            (renderNum, target, eventType) => Unit
    * @param onFormDataProgress (descriptor, loaded, total)
    */
  def setHandlers(onHistory: HistoryCallback,
                  onEvent: EventCallback,
                  onFormDataProgress: FormDataProgressCallback): Unit = {
    this.onHistory = onHistory
    this.onEvent = onEvent
    this.onFormDataProgress = onFormDataProgress
  }

  def listenEvent(name: String, preventDefault: Boolean): Unit =
    connection.send(Procedure.ListenEvent, name, preventDefault)

  def uploadForm(id: Id, descriptor: String): Unit =
    connection.send(Procedure.UploadForm, id.mkString, descriptor)

  def focus(id: Id): Unit =
    connection.send(Procedure.Focus, id.mkString)

  def extractProperty(id: Id, name: String): F[String] = {
    val descriptor = Random.alphanumeric.take(6).mkString
    val promise = async.promise[String]
    promises.put(descriptor, promise)
    connection.send(Procedure.ExtractProperty, descriptor, id.mkString, name)
    promise.future
  }

  def setProperty[T](id: Id, name: Symbol, value: T): Unit = {
    // TODO setProperty should be dedicated
    connection.send(Procedure.ModifyDom, ModifyDomProcedure.SetAttr, id.mkString, 0, name.name, value, true)
  }

  def changePageUrl(path: Path): Unit =
    connection.send(Procedure.ChangePageUrl, path.toString)

  def setRenderNum(i: Int): Unit =
    connection.send(Procedure.SetRenderNum, i)

  def cleanRoot(): Unit =
    connection.send(Procedure.CleanRoot)

  def reloadCss(): Unit =
    connection.send(Procedure.ReloadCss)

  def startDomChanges(): Unit = {
    domChangesBuffer.append(Procedure.ModifyDom)
  }

  def flushDomChanges(): Unit = {
    connection.send(domChangesBuffer: _*)
    domChangesBuffer.clear()
  }

  def remove(id: Id): Unit =
    domChangesBuffer.append(ModifyDomProcedure.Remove, id.parent.get.mkString, id.mkString)

  def createText(id: Id, text: String): Unit =
    domChangesBuffer.append(ModifyDomProcedure.CreateText, id.parent.get.mkString, id.mkString, text)

  def create(id: Id, xmlNs: String, tag: String): Unit = {
    val parent = id.parent.fold("0")(_.mkString)
    val pXmlns =
      if (xmlNs eq levsha.XmlNs.html.uri) 0
      else xmlNs
    domChangesBuffer.append(ModifyDomProcedure.Create, parent, id.mkString, pXmlns, tag)
  }

  def setAttr(id: Id, xmlNs: String, name: String, value: String): Unit = {
    if (isStyle(name)) {
      val n = escapeName(name, truncate = true)
      domChangesBuffer.append(ModifyDomProcedure.SetStyle, id.mkString, n, value)
    } else {
      val p = isProperty(name)
      val n = escapeName(name, p)
      val pXmlns =
        if (xmlNs eq levsha.XmlNs.html.uri) 0
        else xmlNs
      domChangesBuffer.append(ModifyDomProcedure.SetAttr, id.mkString, pXmlns, n, value, p)
    }
  }

  def removeAttr(id: Id, xmlNs: String, name: String): Unit = {
    if (isStyle(name)) {
      val n = escapeName(name, truncate = true)
      domChangesBuffer.append(ModifyDomProcedure.RemoveStyle, id.mkString, n)
    } else {
      val p = isProperty(name)
      val n = escapeName(name, p)
      val pXmlns =
        if (xmlNs eq levsha.XmlNs.html.uri) 0
        else xmlNs
      domChangesBuffer.append(ModifyDomProcedure.RemoveAttr, id.mkString, pXmlns, n, p)
    }
  }

  private def onReceive(): Unit = connection.received.run {
    case Success(json) =>
      val Array(callbackType, dirtyArgs) = json
        .substring(1, json.length - 1) // remove brackets
        .split(',') // split to tokens
      val args = dirtyArgs
        .substring(1, dirtyArgs.length - 1) // remove ""
      callbackType.toInt match {
        case CallbackType.DomEvent =>
          val Array(renderNum, target, tpe) = args.split(':')
          onEvent(renderNum.toInt, Id(target), tpe)
        case CallbackType.FormDataProgress =>
          val Array(descriptor, loaded, total) = args.split(':')
          onFormDataProgress(descriptor, loaded.toInt, total.toInt)
        case CallbackType.ExtractPropertyResponse =>
          val Array(descriptor, value) = args.split(":", 2)
          promises.remove(descriptor).foreach(_.complete(Success(value)))
        case CallbackType.History =>
          onHistory(Router.Path.fromString(args))
      }
      onReceive()
    case Failure(e) =>
      logger.error("Unable to receive message from client", e)
  }

  onReceive()
}

object ClientSideApi {

  type HistoryCallback = Path => Unit
  type EventCallback = (Int, Id, String) => Unit
  type FormDataProgressCallback = (String, Int, Int) => Unit

  object Procedure {
    final val SetRenderNum = 0 // (n)
    final val CleanRoot = 1 // ()
    final val ListenEvent = 2 // (type, preventDefault)
    final val ExtractProperty = 3 // (id, propertyName, descriptor)
    final val ModifyDom = 4 // (commands)
    final val Focus = 5 // (id) {
    final val ChangePageUrl = 6 // (path)
    final val UploadForm = 7 // (id, descriptor)
    final val ReloadCss = 8 // ()

  }

  object ModifyDomProcedure {
    final val Create = 0 // (id, childId, xmlNs, tag)
    final val CreateText = 1 // (id, childId, text)
    final val Remove = 2 // (id, childId)
    final val SetAttr = 3 // (id, xmlNs, name, value, isProperty)
    final val RemoveAttr = 4 // (id, xmlNs, name, isProperty)
    final val SetStyle = 5 // (id, name, value)
    final val RemoveStyle = 6 // (id, name)
  }

  object CallbackType {
    final val DomEvent = 0 // `$renderNum:$elementId:$eventType`
    final val FormDataProgress = 1 // `$descriptor:$loaded:$total`
    final val ExtractPropertyResponse = 2 // `$descriptor:$value`
    final val History = 3 // URL
  }

}
