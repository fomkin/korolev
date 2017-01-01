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

  def configureServer[F[+_]: Async, S](
      render: Korolev.Render[S],
      head: VDom.Node,
      stateStorage: StateStorage[F, S],
      serverRouter: ServerRouter[F, S]
  ): PartialFunction[Request, F[Response]] = {

    import korolev.Shtml._
    import korolev.Router._
    import misc._

    def renderStatic(request: Request): F[Response] = {
      val (isNewDevice, deviceId) = deviceFromRequest(request)
      val sessionId = Random.alphanumeric.take(16).mkString
      val stateF = serverRouter
        .static(deviceId)
        .toState
        .lift(((), request.path))
        .getOrElse(stateStorage.initial(deviceId))

      val writeResultF = Async[F].flatMap(stateF)(stateStorage.write(deviceId, sessionId, _))

      Async[F].map(writeResultF) { state =>
        val body = render(state)
        val dom = 'html(
          head.copy(children =
            'script(bridgeJs) ::
              'script(
                s"var KorolevSessionId = '$sessionId';\n" +
                s"var KorolevServerRootPath = '${serverRouter.rootPath}';\n" +
                  korolevJs
              ) ::
                head.children),
          body
        )
        Response.HttpResponse(
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
        val response = Response.HttpResponse(stream, contentType, None)
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
        Async[F].map(stateStorage.read(deviceId, sessionId)) { state =>
          // Create Korolev with dynamic router
          val router = serverRouter.dynamic(deviceId, sessionId)
          val korolev = Korolev(jsAccess, state, render, router, fromScratch = false)
          // Subscribe on state updates an push them to storage
          korolev.subscribe(state =>
            stateStorage.write(deviceId, sessionId, state))
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
