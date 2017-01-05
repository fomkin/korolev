package korolev

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.charset.StandardCharsets
import java.util.UUID

import bridge.JSAccess

import scala.collection.mutable
import scala.io.Source
import scala.language.higherKinds
import scala.util.Random

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
package object server {

  type MimeTypes = String => Option[String]

  def korolevService[F[+_]: Async, S, M](
    config: KorolevServiceConfig[F, S, M]
  ): PartialFunction[Request, F[Response]] = {

    import misc._

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
        val body = config.render(state)
        val dom = 'html(
          config.head.copy(children =
            'script(bridgeJs) ::
              'script(
                s"var KorolevSessionId = '$sessionId';\n" +
                s"var KorolevServerRootPath = '${config.serverRouter.rootPath}';\n" +
                  korolevJs
              ) ::
            config.head.children),
          body
        )
        Response.Http(
          body = {
            val html = "<!DOCTYPE html>" + dom.html
            val bytes = html.getBytes(StandardCharsets.UTF_8)
            new ByteArrayInputStream(bytes)
          },
          fileExtension = htmlContentType,
          deviceId = Some(deviceId)
        )
      }
    }

    object matchStatic {

      /**
        * @return (InputStream with resource, ContentType)
        */
      def unapply(req: Request): Option[(InputStream, String)] =
        req.path match {
          case Root => None
          case path @ _ / fileName =>
            val fsPath = s"/static${path.toString}"
            val stream = getClass.getResourceAsStream(fsPath)
            Option(stream) map { stream =>
              val contentType = fileName.lastIndexOf('.') match {
                case -1 => binaryContentType
                case index => fileName.substring(index + 1)
              }
              (stream, contentType)
            }
        }
    }

    def deviceFromRequest(request: Request): (Boolean, String) = {
      request.cookie("device") match {
        case None => true -> UUID.randomUUID().toString
        case Some(deviceId) => false -> deviceId
      }
    }

    val service: PartialFunction[Request, F[Response]] = {
      case matchStatic(stream, contentType) =>
        val response = Response.Http(stream, contentType, None)
        Async[F].pure(response)
      case Request(Root / "bridge" / deviceId / sessionId, _, _) =>
        // Queue
        val sendingQueue = mutable.Buffer.empty[String]
        var subscriber = Option.empty[String => Unit]
        val jsAccess = {
          val appendQueue = (message: String) => sendingQueue.append(message)
          JsonQueuedJsAccess(subscriber.fold(appendQueue)(identity)(_))
        }
        // Session storage access
        Async[F].map(config.stateStorage.read(deviceId, sessionId)) { state =>
          // Create Korolev with dynamic router
          val dux = Dux[F, S](state)
          val router = config.serverRouter.dynamic(deviceId, sessionId)
          val env = config.envConfigurator(deviceId, sessionId, dux.apply)
          val korolev = Korolev(dux, jsAccess, state, config.render, router, env.onMessage, fromScratch = false)
          // Subscribe on state updates an push them to storage
          korolev.subscribe(state => config.stateStorage.write(deviceId, sessionId, state))
          korolev.onDestroy(env.onDestroy)
          // Initialize websocket
          Response.WebSocket(
            destroyHandler = () => korolev.destroy(),
            publish = jsAccess.receive,
            subscribe = { newSubscriber =>
              sendingQueue.foreach(newSubscriber)
              sendingQueue.clear()
              subscriber = Some(newSubscriber)
            }
          )
        }
      case request => renderStatic(request)
    }

    service
  }

  private[server] object misc {
    val htmlContentType = "html"
    val binaryContentType = "bin"
    val korolevJs = {
      val stream =
        classOf[Korolev].getClassLoader.getResourceAsStream("korolev.js")
      Source.fromInputStream(stream).mkString
    }
    val bridgeJs = {
      val stream =
        classOf[JSAccess[List]].getClassLoader.getResourceAsStream("bridge.js")
      Source.fromInputStream(stream).mkString
    }
  }
}
