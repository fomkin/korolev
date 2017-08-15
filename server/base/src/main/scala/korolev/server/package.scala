package korolev

import java.io.{ByteArrayOutputStream, InputStream}
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import korolev.Async._
import korolev.util.Scheduler
import levsha.RenderContext
import levsha.impl.{AbstractTextRenderContext, TextPrettyPrintingConfig}
import slogging.LazyLogging

import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.util.{Failure, Random, Success, Try}

package object server extends LazyLogging {

  type MimeTypes = String => Option[String]
  type KorolevService[F[+_]] = PartialFunction[Request, F[Response]]

  def korolevService[F[+_]: Async, S, M](
    mimeTypes: MimeTypes,
    config: KorolevServiceConfig[F, S, M]
  )(implicit scheduler: Scheduler[F]): KorolevService[F] = {

    import misc._

    val sessions = TrieMap.empty[String, KorolevSession[F]]

    def renderStatic(request: Request): F[Response] = {
      val (_, deviceId) = deviceFromRequest(request)
      val sessionId = Random.alphanumeric.take(16).mkString
      val stateF = config.serverRouter
        .static(deviceId)
        .toState
        .lift(((), request.path))
        .getOrElse(config.stateStorage.initial(deviceId))

      val writeResultF = Async[F].flatMap(stateF)(config.stateStorage.write(deviceId, sessionId, _))

      Async[F].map(writeResultF) { state =>
        val dsl = new levsha.TemplateDsl[ApplicationContext.Effect[F, S, M]]()
        def createTextRenderContext() = {
          new AbstractTextRenderContext[ApplicationContext.Effect[F, S, M]] {
            val prettyPrinting = TextPrettyPrintingConfig.noPrettyPrinting
            override def addMisc(misc: ApplicationContext.Effect[F, S, M]): Unit = misc match {
              case ApplicationContext.ComponentEntry(component, value, _) =>
                val rc = this.asInstanceOf[RenderContext[ApplicationContext.Effect[F, Any, Any]]]
                component.render(value).apply(rc)
              case _ => ()
            }
          }
        }
        val textRenderContext = createTextRenderContext()
        import dsl._

        val document = 'html(
          'head(
            'script('language /= "javascript",
              s"""window['KorolevConfig'] = {
                 |  'sessionId': '$sessionId',
                 |  'serverRootPath': '${config.serverRouter.rootPath}',
                 |  'connectionLostWidget': '${
                   val textRenderContext = createTextRenderContext()
                   config.connectionLostWidget(textRenderContext)
                   textRenderContext.mkString
                 }'
                 |};""".stripMargin
            ),
            'script('src /= "korolev-client.min.js"),
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
            "set-cookie" -> s"device=$deviceId"
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

    def deviceFromRequest(request: Request): (Boolean, String) = {
      request.cookie("device") match {
        case None => true -> Random.alphanumeric.take(16).mkString
        case Some(deviceId) => false -> deviceId
      }
    }

    def makeSessionKey(deviceId: String, sessionId: String): String =
      s"$deviceId-$sessionId"

    def createSession(deviceId: String, sessionId: String): F[KorolevSession[F]] = {

      val sendingQueue = new ConcurrentLinkedQueue[String]()
      val subscriber = new AtomicReference(Option.empty[String => Unit])
      val jsAccess = {
        val addToQueue: String => Unit = { message =>
          sendingQueue.add(message)
          ()
        }
        JsonQueuedJsAccess { message =>
          val fOpt = subscriber.getAndSet(None)
          fOpt.fold(addToQueue)(identity)(message)
        }
      }

      // Session storage access
      config.stateStorage.read(deviceId, sessionId) flatMap {
        case Some(state) => Async[F].pure((false, state))
        case None => config.stateStorage.initial(deviceId).map(state => (true, state))
      } map { case (isNew, state) =>

        // Create Korolev with dynamic router
        val dux = StateManager[F, S](state)
        val router = config.serverRouter.dynamic(deviceId, sessionId)
        val env = config.envConfigurator(deviceId, sessionId, dux.apply)
        val korolev = Korolev(
          makeSessionKey(deviceId, sessionId), jsAccess, state, config.render, router, env.onMessage,
          fromScratch = isNew)
        // Subscribe on state updates an push them to storage
        // TODO onDestroy and on state change
        //korolev.stateManager.subscribe(state => config.stateStorage.write(deviceId, sessionId, state))
        //korolev.stateManager.onDestroy(env.onDestroy)

        new KorolevSession[F] {

          val aliveRef = new AtomicBoolean(true)
          val currentPromise = new AtomicReference(Option.empty[Async.Promise[F, String]])

          // Put the session to registry
          val sessionKey = makeSessionKey(deviceId, sessionId)
          sessions.put(sessionKey, this)

          def publish(message: String): F[Unit] = {
            Async[F].pure(jsAccess.receive(message))
          }

          def nextMessage: F[String] = {
            if (sendingQueue.isEmpty) {
              val promise = Async[F].promise[String]
              currentPromise.set(Some(promise))
              subscriber.set(Some(m => promise.complete(Success(m))))
              promise.future
            } else {
              val message = sendingQueue.poll()
              Async[F].pure(message)
            }
          }


          def resolveFormData(descriptor: String, formData: Try[FormData]): Unit = {
            korolev.resolveFormData(descriptor, formData)
          }

          def destroy(): F[Unit] = {
            if (aliveRef.getAndSet(false)) {
              currentPromise.get() foreach { promise =>
                promise.complete(Failure(new SessionDestroyedException("Session has been closed")))
              }
              // TODO destroy
              //korolev.stateManager.destroy()
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
