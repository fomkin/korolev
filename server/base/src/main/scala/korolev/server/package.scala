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

package korolev

import java.io.{ByteArrayOutputStream, InputStream}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import korolev.Async._
import korolev.Context.File
import korolev.internal.{ApplicationInstance, Connection}
import korolev.state._

import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

package object server {

  type StateStorage[F[_], S] = korolev.state.StateStorage[F, S]
  val StateStorage = korolev.state.StateStorage

  type MimeTypes = String => Option[String]
  type KorolevService[F[_]] = PartialFunction[Request[F], F[Response]]

  def korolevService
      [
        F[_]: Async,
        S: StateSerializer: StateDeserializer,
        M
      ] (
        mimeTypes: MimeTypes,
        config: KorolevServiceConfig[F, S, M]
      ): KorolevService[F] = {

    import config.reporter
    import misc._
    import reporter.Implicit

    val sessions = TrieMap.empty[String, KorolevSession[F]]

    def renderStatic(request: Request[F]): F[Response] =
      for {
        deviceId <- deviceFromRequest(request)
        sessionId <- config.idGenerator.generateSessionId()
        manager <- config.stateStorage.get(deviceId, sessionId)
        state <- config.router.toState
          .lift(request.path)
          .fold(Async[F].pure(manager.default))(f => f(manager.default))
      } yield {
        val dsl = new levsha.dsl.SymbolDsl[Context.Effect[F, S, M]]()
        val textRenderContext = new HtmlRenderContext[F, S, M]()
        val rootPath = config.rootPath
        val clw = {
          val textRenderContext = new HtmlRenderContext[F, S, M]()
          config.connectionLostWidget(textRenderContext)
          textRenderContext.mkString
        }
        val heartbeatInterval = config.heartbeatInterval.toMillis

        import dsl._

        val kfg = s"window['kfg']={sid:'$sessionId',r:'$rootPath',clw:'$clw',heartbeatInterval:$heartbeatInterval}"
        val document = 'html(
          'head(
            'script('language /= "javascript", kfg),
            'script('src /= config.rootPath + "static/korolev-client.min.js"),
            config.head
          ),
          config.render(state)
        )

        // Render document to textRenderContext
        document(textRenderContext)

        Response.Http(
          status = Response.Status.Ok,
          headers = Seq(
            "content-type" -> htmlContentType,
            "set-cookie" -> s"${Cookies.DeviceId}=$deviceId; Path=${config.rootPath}"
          ),
          body = Some {
            val sb = mutable.StringBuilder.newBuilder
            val html = sb
              .append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.1//EN\" \"http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd\">")
              .append('\n')
              .append(textRenderContext.builder)
              .mkString
            html.getBytes(StandardCharsets.UTF_8)
          }
        )
      }

    def inputStreamToBytes(stream: InputStream): Array[Byte] = {
      val bufferSize = 32
      val baos = new ByteArrayOutputStream(Math.max(bufferSize, stream.available()))
      val buffer = new Array[Byte](bufferSize)

      var lastBytesRead = 0
      while ({
        lastBytesRead = stream.read(buffer)
        lastBytesRead != -1
      }) {
        baos.write(buffer, 0, lastBytesRead)
      }

      baos.toByteArray
    }

    def deviceFromRequest(request: Request[F]): F[DeviceId] =
      request.cookie(Cookies.DeviceId) match {
        case None => config.idGenerator.generateDeviceId()
        case Some(deviceId) => Async[F].pure(deviceId)
      }

    def makeSessionKey(deviceId: DeviceId, sessionId: SessionId): String =
      s"$deviceId-$sessionId"

    def createSession(deviceId: DeviceId, sessionId: SessionId): F[KorolevSession[F]] = {

      val connection = new Connection[F]()

      for {
        isOld <- config.stateStorage.exists(deviceId, sessionId)
        stateManager <- config.stateStorage.get(deviceId, sessionId)
        qualifiedSessionId = QualifiedSessionId(deviceId, sessionId)
        korolev = new ApplicationInstance(
          qualifiedSessionId, connection,
          stateManager, stateManager.default,
          config.render, config.router, fromScratch = !isOld,
          reporter
        )
        env <- config.envConfigurator.configure(korolev.topLevelComponentInstance.browserAccess)
      } yield {

        // Subscribe to events to publish them to env
        korolev.topLevelComponentInstance.setEventsSubscription { message: M =>
          env.onMessage(message).runIgnoreResult
        }

        new KorolevSession[F] {

          val aliveRef = new AtomicBoolean(true)
          val currentPromise = new AtomicReference(Option.empty[Async.Promise[F, String]])
          val fileDownloadInfoMap: mutable.Map[String, Promise[F, LazyBytes[F]]] =
            mutable.Map.empty // `descriptor/file-name` -> promise(bytes_proxy)

          // Put the session to registry
          val sessionKey: String = makeSessionKey(deviceId, sessionId)
          sessions.put(sessionKey, this)

          def publish(message: String): F[Unit] = {
            connection.receive(message)
            Async[F].unit
          }

          def nextMessage: F[String] =
            connection.sent

          def resolveFormData(descriptor: String, formData: Try[FormData]): Unit = {
            korolev.topLevelComponentInstance.resolveFormData(descriptor, formData)
          }

          def fileDownloadInfoKey(descriptor: String, name: String) =
            s"$descriptor/$name"

          def resolveFile(descriptor: String, name: String, bytes: Try[LazyBytes[F]]): Unit = {
            val key = fileDownloadInfoKey(descriptor, name)
            fileDownloadInfoMap.remove(key).foreach { promise =>
              promise.complete(bytes)
            }
          }

          def fileDownloadInfo(descriptor: String, filesInfo: Map[String, Long]): Unit = fileDownloadInfoMap.synchronized {
            val files = filesInfo map {
              case (name, size) =>
                val promise = Async[F].promise[LazyBytes[F]]
                val proxy = LazyBytes(
                  pull = () => Async[F].flatMap(promise.async)(_.pull()),
                  cancel = () => Async[F].flatMap(promise.async)(_.cancel()),
                  finished = Async[F].flatMap(promise.async)(_.finished),
                  size = Some(size)
                )
                val key = fileDownloadInfoKey(descriptor, name)
                fileDownloadInfoMap.put(key, promise)
                File(name, proxy)
            }
            korolev.topLevelComponentInstance.resolveFile(descriptor, files.toList)
          }

          def destroy(): F[Unit] = {
            if (aliveRef.getAndSet(false))
              env.onDestroy()
                .recover {
                  case ex: Throwable =>
                    reporter.error("Error destroying environment", ex)
                    ()
                }
                .map { _ =>
                  currentPromise.get() foreach { promise =>
                    promise.complete(Failure(new SessionDestroyedException("Session has been closed")))
                  }

                  config.stateStorage.remove(deviceId, sessionId)
                  sessions.remove(sessionKey)

                  ()
                }
            else Async[F].unit
          }
        }
      }
    }

    val formDataCodec = new FormDataCodec(config.maxFormDataEntrySize)

    val response404 = Async[F].pure[Response](Response.Http(Response.Status.NotFound, None, Seq.empty))

    val service: PartialFunction[Request[F], F[Response]] = {
      case Request(Root / "static", _, _, _, _) => response404
      case Request(path, _, _, _, _) if path.startsWith("static") =>
        val fsPath = path.toString
        Option(this.getClass.getResourceAsStream(fsPath)) match {
          case None => response404
          case Some(stream) =>
            val _ / fileName = path
            val fileExtension = fileName.lastIndexOf('.') match {
              case -1 => "bin"
              case index => fileName.substring(index + 1)
            }
            val bytes = inputStreamToBytes(stream)
            val headers = mimeTypes(fileExtension).fold(Seq.empty[(String, String)]) { fe =>
              Seq("content-type" -> fe)
            }
            Async[F].pure(Response.Http(Response.Status.Ok, Some(bytes), headers))
        }
      case r @ Request(Root / "bridge" / deviceId / sessionId / "form-data" / descriptor, _, _, headers, _) =>
        sessions.get(makeSessionKey(deviceId, sessionId)) match {
          case Some(session) =>
            val boundaryOpt = headers collectFirst {
              case (k, v) if k.toLowerCase == "content-type" && v.contains("multipart/form-data") => v
            } flatMap {
              _.split(';')
                .toList
                .filter(_.contains('='))
                .map(_.split('=').map(_.trim))
                .collectFirst { case Array("boundary", s) => s }
            }
            boundaryOpt match {
              case None =>
                val error = "Content-Type should be `multipart/form-data`"
                val res = Response.Http(Response.Status.BadRequest, error)
                Async[F].pure(res)
              case Some(boundary) =>
                r.body.toStrict.map { body =>
                  val formData = Try(formDataCodec.decode(ByteBuffer.wrap(body), boundary))
                  session.resolveFormData(descriptor, formData)
                  Response.Http(Response.Status.Ok, None)
                }
            }
          case None =>
            Async[F].pure(Response.Http(Response.Status.BadRequest, "Session doesn't exist"))
        }
      case Request(Root / "bridge" / deviceId / sessionId / "file" / descriptor / "info", _, _, _, body) =>
        sessions.get(makeSessionKey(deviceId, sessionId)) match {
          case Some(session) =>
            Async[F].map(body.toStrictUtf8) { info =>
              val files = info
                .split("\n")
                .map { entry =>
                  val slash = entry.lastIndexOf('/')
                  (entry.substring(0, slash), entry.substring(slash + 1).toLong)
                }
                .toMap
              session.fileDownloadInfo(descriptor, files)
              Response.Http(Response.Status.Ok, None)
            }
          case None =>
            Async[F].pure(Response.Http(Response.Status.BadRequest, "Session doesn't exist"))
        }
      case Request(Root / "bridge" / deviceId / sessionId / "file" / descriptor, _, _, headers, body) =>
        val result =
          for {
            session <- sessions.get(makeSessionKey(deviceId, sessionId))
            name <- headers.collectFirst { case ("x-name", v) => v }
          } yield {
            session.resolveFile(descriptor, name, Success(body))
          }
        result match {
          case Some(_) => body.finished.map(_ => Response.Http(Response.Status.Ok, None))
          case None => Async[F].pure(Response.Http(Response.Status.BadRequest))
        }
      case Request(Root / "bridge" / "long-polling" / deviceId / sessionId / "publish", _, _, _, body) =>
        sessions.get(makeSessionKey(deviceId, sessionId)) match {
          case Some(session) =>
            for {
              bytes <- body.toStrict
              message = new String(bytes, StandardCharsets.UTF_8)
              _ <- session.publish(message)
            } yield {
              Response.Http(Response.Status.Ok)
            }
          case None =>
            Async[F].pure(Response.Http(Response.Status.BadRequest, "Session doesn't exist"))
        }
      case Request(Root / "bridge" / "long-polling" / deviceId / sessionId / "subscribe", _, _, _, _) =>
        val sessionAsync = sessions.get(makeSessionKey(deviceId, sessionId)) match {
          case Some(x) => Async[F].pure(x)
          case None => createSession(deviceId, sessionId)
        }
        sessionAsync.flatMap { session =>
          session.nextMessage.map { message =>
            Response.Http(Response.Status.Ok,
              body = Some(message.getBytes(StandardCharsets.UTF_8)),
              headers = Seq("Cache-Control" -> "no-cache")
            )
          }
        } recover {
          case _: SessionDestroyedException =>
            Response.Http(Response.Status.Gone, "Session has been destroyed")
        }
      case Request(Root / "bridge" / "web-socket" / deviceId / sessionId, _, _, _, _) =>
        val sessionAsync = sessions.get(makeSessionKey(deviceId, sessionId)) match {
          case Some(x) => Async[F].pure(x)
          case None => createSession(deviceId, sessionId)
        }
        sessionAsync map { session =>
          Response.WebSocket(
            destroyHandler = () => session.destroy() run {
              case Success(_) => // do nothing
              case Failure(e) => reporter.error("An error occurred during destroying the session", e)
            },
            publish = message => session.publish(message) run {
              case Success(_) => // do nothing
              case Failure(e) => reporter.error("An error occurred during publishing message to session", e)
            },
            subscribe = { newSubscriber =>
              def aux(): Unit = session.nextMessage run {
                case Success(message) =>
                  newSubscriber(message)
                  aux()
                case Failure(_: SessionDestroyedException) => // Do nothing
                case Failure(e) =>
                  reporter.error("An error occurred during polling message from session", e)
              }
              aux()
            }
          )
        }
      case request if request.path == Root || config.router.toState.isDefinedAt(request.path) =>
        renderStatic(request)
      case _ => response404
    }

    service
  }

  @deprecated("Use Router.empty instead", "0.12.0")
  def emptyRouter[F[_]: Async, S]: Router[F, S] = Router.empty[F, S]

  private[server] object misc {
    val htmlContentType = "text/html; charset=utf-8"
    val binaryContentType = "application/octet-stream"
  }

}
