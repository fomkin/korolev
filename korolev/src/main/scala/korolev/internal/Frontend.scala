/*
 * Copyright 2017-2018 Aleksey Fomkin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package korolev.internal

import java.util.concurrent.atomic.AtomicInteger

import korolev.Context.File
import korolev.{FormData, Router}
import korolev.Router.Path
import korolev.effect.io.LazyBytes
import korolev.effect.syntax._
import korolev.effect.{AsyncTable, Effect, Queue, Reporter, Stream}
import levsha.Id
import levsha.impl.DiffRenderContext.ChangesPerformer

import scala.annotation.switch
import scala.util.{Failure, Success}

/**
  * Typed interface to client side
  */
final class Frontend[F[_]: Effect](incomingMessages: Stream[F, String])(implicit reporter: Reporter) {

  import Frontend._

  private val lastDescriptor = new AtomicInteger(0)
  private val remoteDomChangesPerformer = new RemoteDomChangesPerformer()

  private val stringPromises = AsyncTable.empty[F, String, String]
  private val formDataPromises = AsyncTable.empty[F, String, FormData]
  private val filesPromises = AsyncTable.empty[F, String, List[File[LazyBytes[F]]]]

  private var onHistory: HistoryCallback = _
  private var onEvent: EventCallback = _

  /**
    * @param onEvent            (renderNum, target, eventType) => Unit
    */
  def setHandlers(onHistory: HistoryCallback,
                  onEvent: EventCallback): Unit = {
    this.onHistory = onHistory
    this.onEvent = onEvent
  }

  private val outgoingQueue = Queue[F, String]()

  val outgoingMessages: Stream[F, String] = outgoingQueue.stream

  def send(args: Any*): Unit =
    sendF(args: _*).runAsyncForget

  def sendF(args: Any*): F[Unit] = {

    def escape(sb: StringBuilder, s: String, unicode: Boolean): Unit = {
      sb.append('"')
      var i = 0
      val len = s.length
      while (i < len) {
        (s.charAt(i): @switch) match {
          case '"'  => sb.append("\\\"")
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

    outgoingQueue.offer(sb.mkString)
  }

  def listenEvent(name: String, preventDefault: Boolean): F[Unit] =
    sendF(Procedure.ListenEvent.code, name, preventDefault)

  def uploadForm(id: Id): F[FormData] =
    for {
      descriptor <- nextDescriptor()
      _ <- sendF(Procedure.UploadForm.code, id.mkString, descriptor)
      result <- formDataPromises.get(descriptor)
    } yield result

  def uploadFiles(id: Id): F[List[File[LazyBytes[F]]]] =
    for {
      descriptor <- nextDescriptor()
      _ <- sendF(Procedure.UploadFiles.code, id.mkString, descriptor)
      files <- filesPromises.get(descriptor)
    } yield files

  def focus(id: Id): F[Unit] =
    sendF(Procedure.Focus.code, id.mkString)

  private def nextDescriptor() =
    Effect[F].delay(lastDescriptor.getAndIncrement().toString)

  def extractProperty(id: Id, name: String): F[String] = {
    for {
      descriptor <- nextDescriptor()
      _ <- sendF(Procedure.ExtractProperty.code, descriptor, id.mkString, name)
      result <- stringPromises.get(descriptor)
    } yield result
  }

  def setProperty(id: Id, name: String, value: Any): F[Unit] = {
    // TODO setProperty should be dedicated
    sendF(Procedure.ModifyDom.code, ModifyDomProcedure.SetAttr.code, id.mkString, 0, name, value, true)
  }

  def evalJs(code: String): F[String] =
    for {
      descriptor <- nextDescriptor()
      _ <- sendF(Procedure.EvalJs.code, descriptor, code)
      result <- stringPromises.get(descriptor)
    } yield result

  def resetForm(id: Id): F[Unit] =
    sendF(Procedure.RestForm.code, id.mkString)

  def changePageUrl(path: Path): Unit =
    send(Procedure.ChangePageUrl.code, path.mkString)

  def setRenderNum(i: Int): F[Unit] =
    sendF(Procedure.SetRenderNum.code, i)

  def cleanRoot(): F[Unit] =
    sendF(Procedure.CleanRoot.code)

  def reloadCss(): F[Unit] =
    sendF(Procedure.ReloadCss.code)

  def extractEventData(renderNum: Int): F[String] =
    for {
      descriptor <- nextDescriptor()
      _ <- sendF(Procedure.ExtractEventData.code, descriptor, renderNum)
      result <- stringPromises.get(descriptor)
    } yield result

  def performDomChanges(f: ChangesPerformer => Unit): F[Unit] =
    for {
      _ <- Effect[F]
        .delay {
          remoteDomChangesPerformer.buffer.append(Procedure.ModifyDom.code)
          f(remoteDomChangesPerformer)
        }
      _ <- sendF(remoteDomChangesPerformer.buffer.toSeq: _*)
      _ <- Effect[F].delay(remoteDomChangesPerformer.buffer.clear())
    } yield ()

  private def unescapeJsonString(s: String): String = {
    val sb = StringBuilder.newBuilder
    var i = 1
    val len = s.length - 1
    while (i < len) {
      val c = s.charAt(i)
      var charsConsumed = 0
      if (c != '\\') {
        charsConsumed = 1
        sb.append(c)
      } else {
        charsConsumed = 2
        (s.charAt(i + 1): @switch) match {
          case '\\' => sb.append('\\')
          case '"'  => sb.append('"')
          case 'b'  => sb.append('\b')
          case 'f'  => sb.append('\f')
          case 'n'  => sb.append('\n')
          case 'r'  => sb.append('\r')
          case 't'  => sb.append('\t')
          case 'u' =>
            val code = s.substring(i + 2, i + 6)
            charsConsumed = 6
            sb.append(Integer.parseInt(code, 16).toChar)
        }
      }
      i += charsConsumed
    }
    sb.result()
  }

  def resolveFiles(descriptor: String, files: List[File[LazyBytes[F]]]): F[Unit] =
    filesPromises
      .put(descriptor, files)
      .flatMap(_ => filesPromises.remove(descriptor))

  def resolveFormData(descriptor: String, formData: Either[Throwable, FormData]): F[Unit] =
    formDataPromises
      .putEither(descriptor, formData)
      .flatMap(_ => formDataPromises.remove(descriptor))

  private def onReceive(): Unit = incomingMessages.pull().runAsync {
    case Success(Some(json)) =>
      val tokens = json
        .substring(1, json.length - 1) // remove brackets
        .split(",", 2) // split to tokens
      val callbackType = tokens(0)
      val args =
        if (tokens.length > 1) unescapeJsonString(tokens(1))
        else ""

      callbackType.toInt match {
        case CallbackType.DomEvent.code =>
          val Array(renderNum, target, tpe) = args.split(':')
          onEvent(renderNum.toInt, Id(target), tpe)
        case CallbackType.FormDataProgress.code =>
//          val Array(descriptor, loaded, total) = args.split(':')
//          onFormDataProgress(descriptor, loaded.toInt, total.toInt)
        case CallbackType.ExtractPropertyResponse.code =>
          val Array(descriptor, propertyType, value) = args.split(":", 3)
          propertyType.toInt match {
            case PropertyType.Error.code =>
              stringPromises
                .fail(descriptor, ClientSideException(value))
                .flatMap(_ => stringPromises.remove(descriptor))
            case _ =>
              stringPromises
                .put(descriptor, value)
                .flatMap(_ => stringPromises.remove(descriptor))
          }
        case CallbackType.ExtractEventDataResponse.code =>
          val Array(descriptor, value) = args.split(":", 2)
          stringPromises
            .put(descriptor, value)
            .flatMap(_ => stringPromises.remove(descriptor))
        case CallbackType.History.code =>
          onHistory(Router.Path.fromString(args))
        case CallbackType.EvalJsResponse.code =>
          val Array(descriptor, status, json) = args.split(":", 3)
          status.toInt match {
            case EvalJsStatus.Success.code =>
              stringPromises
                .put(descriptor, json)
                .flatMap(_ => stringPromises.remove(descriptor))
            case EvalJsStatus.Failure.code =>
              stringPromises
                .fail(descriptor, ClientSideException("JavaScript evaluation error"))
                .flatMap(_ => stringPromises.remove(descriptor))
          }
        case CallbackType.Heartbeat.code =>
        // ignore
      }
      onReceive()
    case Success(None) => ()
    case Failure(e) =>
      reporter.error("Unable to receive message from client", e)
  }

  onReceive()
}

object Frontend {

  type HistoryCallback = Path => Unit
  type EventCallback = (Int, Id, String) => Unit

  sealed abstract class Procedure(final val code: Int)

  object Procedure {
    case object SetRenderNum extends Procedure(0) // (n)
    case object CleanRoot extends Procedure(1) // ()
    case object ListenEvent extends Procedure(2) // (type, preventDefault)
    case object ExtractProperty extends Procedure(3) // (id, propertyName, descriptor)
    case object ModifyDom extends Procedure(4) // (commands)
    case object Focus extends Procedure(5) // (id) {
    case object ChangePageUrl extends Procedure(6) // (path)
    case object UploadForm extends Procedure(7) // (id, descriptor)
    case object ReloadCss extends Procedure(8) // ()
    case object KeepAlive extends Procedure(9) // ()
    case object EvalJs extends Procedure(10) // (code)
    case object ExtractEventData extends Procedure(11) // (descriptor, renderNum)
    case object UploadFiles extends Procedure(12) // (id, descriptor)
    case object RestForm extends Procedure(13) // (id)

    val All = Set(
      SetRenderNum,
      CleanRoot,
      ListenEvent,
      ExtractProperty,
      ModifyDom,
      Focus,
      ChangePageUrl,
      UploadForm,
      ReloadCss,
      KeepAlive,
      EvalJs,
      ExtractEventData,
      UploadFiles,
      RestForm
    )

    def apply(n: Int): Option[Procedure] =
      All.find(_.code == n)
  }

  sealed abstract class ModifyDomProcedure(final val code: Int)

  object ModifyDomProcedure {
    case object Create extends ModifyDomProcedure(0) // (id, childId, xmlNs, tag)
    case object CreateText extends ModifyDomProcedure(1) // (id, childId, text)
    case object Remove extends ModifyDomProcedure(2) // (id, childId)
    case object SetAttr extends ModifyDomProcedure(3) // (id, xmlNs, name, value, isProperty)
    case object RemoveAttr extends ModifyDomProcedure(4) // (id, xmlNs, name, isProperty)
    case object SetStyle extends ModifyDomProcedure(5) // (id, name, value)
    case object RemoveStyle extends ModifyDomProcedure(6) // (id, name)
  }

  sealed abstract class PropertyType(final val code: Int)

  object PropertyType {
    case object String extends PropertyType(0)
    case object Number extends PropertyType(1)
    case object Boolean extends PropertyType(2)
    case object Object extends PropertyType(3)
    case object Error extends PropertyType(4)
  }

  sealed abstract class EvalJsStatus(final val code: Int)

  object EvalJsStatus {
    case object Success extends EvalJsStatus(0)
    case object Failure extends EvalJsStatus(1)
  }

  sealed abstract class CallbackType(final val code: Int)

  object CallbackType {
    case object DomEvent extends CallbackType(0) // `$renderNum:$elementId:$eventType`
    case object FormDataProgress extends CallbackType(1) // `$descriptor:$loaded:$total`
    case object ExtractPropertyResponse extends CallbackType(2) // `$descriptor:$value`
    case object History extends CallbackType(3) // URL
    case object EvalJsResponse extends CallbackType(4) // `$descriptor:$status:$value`
    case object ExtractEventDataResponse extends CallbackType(5) // `$descriptor:$dataJson`
    case object Heartbeat extends CallbackType(6) // `$descriptor:$anyvalue`

    final val All = Set(DomEvent,
                        FormDataProgress,
                        ExtractPropertyResponse,
                        History,
                        EvalJsResponse,
                        ExtractEventDataResponse,
                        Heartbeat)

    def apply(n: Int): Option[CallbackType] =
      All.find(_.code == n)
  }

  case class ClientSideException(message: String) extends Exception(message)
}
