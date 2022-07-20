package korolev.engine

import korolev.context.Access.{FileHandler, FileHandlerImpl}
import korolev.context.State.UpdatePolicy
import korolev.context.{Access, Binding, CII, State}
import korolev.data.Bytes
import korolev.effect.syntax._
import korolev.effect.{Effect, Queue, Stream, Var}
import korolev.internal.Frontend
import korolev.util.JsCode
import korolev.web.FormData

case class AccessImpl[F[_] : Effect](frontend: Frontend[F],
                                     viewState: Var[F, ViewState],
                                     renderState: Var[F, RenderState[F]],
                                     statesQueue: Queue[F, ViewState],
                                     maybeCii: Option[CII]) extends Access[F] {


  def getState[T](state: State[T]): F[T] =
    for (vs <- viewState.get) yield vs.take(state, maybeCii)

  def setState[T](state: State[T], value: T): F[Unit] =
    for {
      newVs <- viewState.update(_.add(state, maybeCii, value))
      _ <- statesQueue.enqueue(newVs)
    } yield ()

  def updateState[T](state: State[T])(f: T => T): F[Unit] =
    for {
      newVs <- viewState.update(vs => vs.add(state, maybeCii, f(vs.take(state, maybeCii))))
      _ <- statesQueue.enqueue(newVs)
    } yield ()

  def updateStateAsync[T](state: State[T], policy: UpdatePolicy)(f: T => F[T]): F[Boolean] =
    policy match {
      case UpdatePolicy.JustUpdate =>
        for {
          vs <- viewState.get
          newValue <- f(vs.take(state, maybeCii))
          newVs = vs.add(state, maybeCii, newValue)
          _ <- viewState.set(newVs)
          _ <- statesQueue.enqueue(newVs)
        } yield true
      case UpdatePolicy.CompareAndSet =>
        for {
          maybeNewVs <- viewState.tryUpdateF { vs =>
            f(vs.take(state, maybeCii)).map { newValue =>
              vs.add(state, maybeCii, newValue)
            }
          }
          _ <- maybeNewVs.fold(Effect[F].unit)(newVs => statesQueue.enqueue(newVs))
        } yield maybeNewVs.nonEmpty
      case UpdatePolicy.CompareAndSetRetry =>
        for {
          newVs <- viewState.updateF { vs =>
            f(vs.take(state, maybeCii)).map { newValue =>
              vs.add(state, maybeCii, newValue)
            }
          }
          _ <- statesQueue.enqueue(newVs)
        } yield true
    }

  def getProperty(element: Binding.Element, propertyName: String): F[String] =
    renderState.get.flatMap { rs =>
      frontend.extractProperty(rs.getId(element), propertyName)
    }

  def setProperty(element: Binding.Element, propertyName: String, value: String): F[Unit] =
    renderState.get.flatMap { rs =>
      frontend.setProperty(rs.getId(element), propertyName, value)
    }

  def setValue(element: Binding.Element, value: String): F[Unit] =
    setProperty(element, "value", value)

  def getValue(element: Binding.Element): F[String] =
    getProperty(element, "value")

  def focus(element: Binding.Element): F[Unit] =
    renderState.get.flatMap { rs =>
      frontend.focus(rs.getId(element))
    }

  def downloadFormData(element: Binding.Element): F[FormData] =
    renderState.get.flatMap { rs =>
      frontend.uploadForm(rs.getId(element))
    }

  def downloadFiles(element: Binding.Element): F[List[(FileHandler, Bytes)]] = {
    downloadFilesAsStream(element).flatMap { streams =>
      Effect[F].sequence {
        streams.map { case (handler, data) =>
          data
            .fold(Bytes.empty)(_ ++ _)
            .map(b => (handler, b))
        }
      }
    }
  }

  def downloadFilesAsStream(element: Binding.Element): F[List[(FileHandler, Stream[F, Bytes])]] = {
    listFiles(element).flatMap { handlers =>
      Effect[F].sequence {
        handlers.map { handler =>
          downloadFileAsStream(handler).map(f => (handler, f))
        }
      }
    }
  }

  /**
   * Get selected file as a stream from input
   */
  def downloadFileAsStream(handler: FileHandler): F[Stream[F, Bytes]] = {
    for {
      rs <- renderState.get
      id = rs.getId(handler.element)
      streams <- frontend.uploadFile(id, handler.fileName)
    } yield streams
  }

  def listFiles(element: Binding.Element): F[List[FileHandler]] =
    for {
      rs <- renderState.get
      id = rs.getId(element)
      files <- frontend.listFiles(id)
    } yield {
      files.map { case (fileName, size) =>
        FileHandlerImpl(fileName, size, element)
      }
    }

  def uploadFile(name: String,
                 stream: Stream[F, Bytes],
                 size: Option[Long],
                 mimeType: String): F[Unit] =
    frontend.downloadFile(name, stream, size, mimeType)

  def resetForm(element: Binding.Element): F[Unit] =
    renderState.get.flatMap { rs =>
      val id = rs.getId(element)
      frontend.resetForm(id)
    }

  def evalJs(code: JsCode): F[String] =
    renderState.get.flatMap { rs =>
      frontend.evalJs(code.mkString(rs.getId))
    }

  def eventData: F[String] =
    renderState.get.flatMap { rs =>
      frontend.extractEventData(rs.renderNum)
    }

  def registerCallback(name: String)(f: String => F[Unit]): F[Unit] =
    frontend.registerCustomCallback(name)(f)
}
