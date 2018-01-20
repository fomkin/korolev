package korolev

import java.io.{ByteArrayOutputStream, InputStream}
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import korolev.Async._
import korolev.internal.{ApplicationInstance, Connection}
import korolev.state.{DeviceId, SessionId, StateDeserializer, StateSerializer}
import levsha.Id
import slogging.LazyLogging

import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.util.{Failure, Success, Try}

package object server extends LazyLogging {

  type StateStorage[F[+_], S] = korolev.state.StateStorage[F, S]
  val StateStorage = korolev.state.StateStorage

  type MimeTypes = String => Option[String]
  type KorolevService[F[+_]] = PartialFunction[Request, F[Response]]

  def korolevService
      [
        F[+_]: Async,
        S: StateSerializer: StateDeserializer,
        M
      ] (
        mimeTypes: MimeTypes,
        config: KorolevServiceConfig[F, S, M]
      ): KorolevService[F] = {

    import misc._

    val sessions = TrieMap.empty[String, KorolevSession[F]]

    def renderStatic(request: Request): F[Response] =
      for {
        deviceId <- deviceFromRequest(request)
        sessionId <- config.idGenerator.generateSessionId()
        state <- config.serverRouter
          .static(deviceId)
          .toState
          .lift(((), request.path))
          .getOrElse(config.stateStorage.createTopLevelState(deviceId))
          .flatMap { state =>
            config.stateStorage.create(deviceId, sessionId).flatMap { manager =>
              manager.write(Id.TopLevel, state).map { _ =>
                state
              }
            }
          }
      } yield {
        val dsl = new levsha.TemplateDsl[Context.Effect[F, S, M]]()
        val textRenderContext = new HtmlRenderContext[F, S, M]()
        val rootPath = config.serverRouter.rootPath
        val clw = {
          val textRenderContext = new HtmlRenderContext[F, S, M]()
          config.connectionLostWidget(textRenderContext)
          textRenderContext.mkString
        }

        import dsl._

        val document = 'html(
          'head(
            'script('language /= "javascript", s"window['kfg']={sid:'$sessionId',r:'$rootPath',clw:'$clw'}"),
            'script('src /= config.serverRouter.rootPath + "korolev-client.min.js"),
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
            "set-cookie" -> s"${Cookies.DeviceId}=$deviceId"
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

    object matchStatic {

      /**
        * @return (resource bytes, ContentType)
        */
      def unapply(req: Request): Option[(Array[Byte], String)] =
        req.path match {
          case Root => None
          case path @ _ / fileName =>
            val fsPath = s"/static${path.toString}"
            val stream = getClass.getResourceAsStream(fsPath)
            Option(stream) map { stream =>
              val fileExtension = fileName.lastIndexOf('.') match {
                case -1 => binaryContentType
                case index => fileName.substring(index + 1)
              }
              (inputStreamToBytes(stream), fileExtension)
            }
        }

      private def inputStreamToBytes(stream: InputStream): Array[Byte] = {
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
    }

    def deviceFromRequest(request: Request): F[DeviceId] =
      request.cookie(Cookies.DeviceId) match {
        case None => config.idGenerator.generateDeviceId()
        case Some(deviceId) => Async[F].pure(deviceId)
      }

    def makeSessionKey(deviceId: DeviceId, sessionId: SessionId): String =
      s"$deviceId-$sessionId"

    def createSession(deviceId: DeviceId, sessionId: SessionId): F[KorolevSession[F]] = {

      val connection = new Connection[F]()

      for {
        maybeStateManager <- config.stateStorage.get(deviceId, sessionId)
        stateManager <- maybeStateManager.fold(config.stateStorage.create(deviceId, sessionId))(Async[F].pure(_))
        isNew = maybeStateManager.isEmpty
        initialState <- config.stateStorage.createTopLevelState(deviceId)
      } yield {
        // Create Korolev with dynamic router
        val router = config.serverRouter.dynamic(deviceId, sessionId)
        val qualifiedSessionId = QualifiedSessionId(deviceId, sessionId)
        val korolev = new ApplicationInstance(
          qualifiedSessionId, connection,
          stateManager, initialState,
          config.render, router, fromScratch = isNew
        )
        val applyTransition = korolev.topLevelComponentInstance.applyTransition _
        val env = config.envConfigurator(deviceId, sessionId, applyTransition)
        // Subscribe to events to publish them to env
        korolev.topLevelComponentInstance.setEventsSubscription(env.onMessage)

        new KorolevSession[F] {

          val aliveRef = new AtomicBoolean(true)
          val currentPromise = new AtomicReference(Option.empty[Async.Promise[F, String]])

          // Put the session to registry
          val sessionKey = makeSessionKey(deviceId, sessionId)
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

          def destroy(): F[Unit] = {
            if (aliveRef.getAndSet(false)) {
              currentPromise.get() foreach { promise =>
                promise.complete(Failure(new SessionDestroyedException("Session has been closed")))
              }
              env.onDestroy()
              sessions.remove(sessionKey)
            }
            Async[F].unit
          }
        }
      }
    }

    val formDataCodec = new FormDataCodec(config.maxFormDataEntrySize)

    val service: PartialFunction[Request, F[Response]] = {
      case matchStatic(stream, fileExtensionOpt) =>
        val headers = mimeTypes(fileExtensionOpt).fold(Seq.empty[(String, String)]) {
          fileExtension =>
            Seq("content-type" -> fileExtension)
        }
        val response = Response.Http(Response.Status.Ok, Some(stream), headers)
        Async[F].pure(response)
      case Request(Root / "bridge" / deviceId / sessionId / "form-data" / descriptor, _, _, headers, body) =>
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
                val formData = Try(formDataCodec.decode(body, boundary))
                session.resolveFormData(descriptor, formData)
                Async[F].pure(Response.Http(Response.Status.Ok, None))
            }
          case None =>
            Async[F].pure(Response.Http(Response.Status.BadRequest, "Session doesn't exist"))
        }
      case Request(Root / "bridge" / "long-polling" / deviceId / sessionId / "publish", _, _, _, body) =>
        sessions.get(makeSessionKey(deviceId, sessionId)) match {
          case Some(session) =>
            val message = new String(body.array(), StandardCharsets.UTF_8)
            session
              .publish(message)
              .map(_ => Response.Http(Response.Status.Ok))
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
              case Failure(e) => logger.error("An error occurred during destroying the session", e)
            },
            publish = message => session.publish(message) run {
              case Success(_) => // do nothing
              case Failure(e) => logger.error("An error occurred during publishing message to session", e)
            },
            subscribe = { newSubscriber =>
              def aux(): Unit = session.nextMessage run {
                case Success(message) =>
                  newSubscriber(message)
                  aux()
                case Failure(_: SessionDestroyedException) => // Do nothing
                case Failure(e) =>
                  logger.error("An error occurred during polling message from session", e)
              }
              aux()
            }
          )
        }
      case request => renderStatic(request)
    }

    service
  }

  private[server] object misc {
    val htmlContentType = "text/html; charset=utf-8"
    val binaryContentType = "application/octet-stream"
  }

}
