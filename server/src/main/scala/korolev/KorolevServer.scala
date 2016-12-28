package korolev

import java.io.{ByteArrayInputStream, InputStream}
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import scala.language.higherKinds
import java.util.UUID

import bridge.JSAccess

import scala.collection.mutable
import scala.io.Source

case class KorolevServer[F[+_]: Async, S](
    // Without defaults
    stateStorage: StateStorage[F, S],
    mimeTypeForExtenstion: String => Option[String],
    serverRouter: ServerRouter[F, S])(
    // With defautls
    port: Int = 8181,
    host: String = InetAddress.getLoopbackAddress.getHostAddress,
    render: Korolev.Render[S] = PartialFunction.empty,
    head: VDom.Node = VDom.Node("head", Nil, Nil, Nil)
) extends Shtml {

  import KorolevServer._
  import Router._
  import misc._

  private def renderStatic(request: Request): F[Response] = {
    val (isNewDevice, deviceId) = deviceFromRequest(request)
    val stateF = serverRouter.
      static(deviceId).
      toState.lift(((), request.path)).
      getOrElse(stateStorage.initial(deviceId))

    Async[F].map(stateF) { state =>
      val body = render(state)
      val dom = 'html(
        head.copy(children =
          'script(bridgeJs) ::
          'script(
            s"var KorolevServerRootPath = '${serverRouter.rootPath}';\n" +
            korolevJs
          ) ::
          head.children
        ),
        body
      )
      Response.HttpResponse(
        body = {
          val html = "<!DOCTYPE html>" + dom.html
          val bytes = html.getBytes(StandardCharsets.UTF_8)
          new ByteArrayInputStream(bytes)
        },
        code = 200,
        contentType = htmlContentType,
        setCookie =
          if (isNewDevice) Map("device" -> deviceId)
          else Map.empty
      )
    }
  }

  private object matchStatic {

    /**
      * @return (InputStream with resource, ContentType)
      */
    def unapply(req: Request): Option[(InputStream, String)] = req.path match {
      case Root => None
      case path @ _ / fileName =>
        val fsPath = s"/static${path}"
        val stream = getClass.getResourceAsStream(fsPath)
        Option(stream) map { stream =>
          val contentType = fileName.lastIndexOf('.') match {
            case -1 => binaryContentType
            case index =>
              val extension = fileName.substring(index + 1)
              mimeTypeForExtenstion(extension) match {
                case Some(mediaType) => mediaType
                case None => binaryContentType
              }
          }
          (stream, contentType)
        }
    }
  }

  private def deviceFromRequest(request: Request): (Boolean, String) = {
    request.cookie("device") match {
      case None => true -> UUID.randomUUID().toString
      case Some(deviceId) => false -> deviceId
    }
  }

  val service: PartialFunction[Request, F[Response]] = {
    case matchStatic(stream, contentType) =>
      val response = Response.HttpResponse(stream, 200, contentType, Map.empty)
      Async[F].pure(response)
    case request @ Request(Root / "bridge" / deviceId / sessionId, params, cookie) =>
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
        val korolev = Korolev(jsAccess, state, render, router, false)
        // Subscribe on state updates an push them to storage
        korolev.subscribe(state => stateStorage.write(deviceId, sessionId, state))
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
}

object KorolevServer {

  case class Request(
    path: Router.Path,
    params: Map[String, String],
    cookie: String => Option[String]
  )

  sealed trait Response

  object Response {
    case class HttpResponse(
      body: InputStream,
      code: Int,
      contentType: String,
      setCookie: Map[String, String]
    ) extends Response

    case class WebSocket(
      publish: String => Unit,
      subscribe: (String => Unit) => Unit,
      destroyHandler: () => Unit
    ) extends Response
  }

  private[korolev] object misc {
    val htmlContentType = "text/html"
    val binaryContentType = "application/octet-stream"
    val korolevJs = {
      val stream = classOf[Korolev].
        getClassLoader.
        getResourceAsStream("korolev.js")
      Source.fromInputStream(stream).mkString
    }
    val bridgeJs = {
      val stream = classOf[JSAccess[List]].
        getClassLoader.
        getResourceAsStream("bridge.js")
      Source.fromInputStream(stream).mkString
    }
  }
}
