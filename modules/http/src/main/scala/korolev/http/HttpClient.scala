package korolev.http

import korolev.data.BytesLike
import korolev.effect.io.{DataSocket, RawDataSocket, SecureDataSocket}
import korolev.effect.syntax._
import korolev.effect.{Decoder, Effect, Stream}
import korolev.http.protocol.WebSocketProtocol.Frame
import korolev.http.protocol.{Http11, WebSocketProtocol}
import korolev.web.{Headers, PathAndQuery, Request, Response}

import java.net.{InetSocketAddress, URI}
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.{Executor, Executors}
import javax.net.ssl.SSLContext
import scala.concurrent.ExecutionContext

class HttpClient[F[_] : Effect, B: BytesLike] private (blockingExecutor: Executor,
                                                       group: AsynchronousChannelGroup,
                                                       incomingBufferSize: Int,
                                                       sslContext: SSLContext)(implicit executor: ExecutionContext) {

  private val http11 = new Http11[B]()

  def apply(method: Request.Method,
            uri: URI,
            headers: Seq[(String, String)],
            contentLength: Option[Long],
            body: Stream[F, B]): F[Response[Stream[F, B]]] = {
    val updatedHeaders =
      if (uri.getUserInfo != null) Headers.basicAuthorization(uri.getUserInfo) +: headers
      else headers
    val request = Request(method, pqFromUri(uri), updatedHeaders, contentLength, body)

    uri.getScheme match {
      case "http" if uri.getPort == -1 => http(new InetSocketAddress(uri.getHost, 80), request)
      case "https" if uri.getPort == -1 => https(new InetSocketAddress(uri.getHost, 443), request)
      case "http" => http(new InetSocketAddress(uri.getHost, uri.getPort), request)
      case "https" => https(new InetSocketAddress(uri.getHost, uri.getPort), request)
      case "ws" | "wss" => Effect[F].fail(new IllegalArgumentException(s"Use HttpClient.webSocket() of HttpClient()"))
      case scheme => Effect[F].fail(new IllegalArgumentException(s"$scheme is not supported"))
    }
  }

  def https(address: InetSocketAddress, request: Request[Stream[F, B]]): F[Response[Stream[F, B]]] =
    for {
      rawSocket <- RawDataSocket.connect(address, buffer = ByteBuffer.allocate(incomingBufferSize), group)
      engine = sslContext.createSSLEngine(address.getHostName, address.getPort)
      socket <- SecureDataSocket.forClientMode(rawSocket, engine, blockingExecutor)
      response <- http(socket, address.getHostName, request)
    } yield response

  def http(address: InetSocketAddress, request: Request[Stream[F, B]]): F[Response[Stream[F, B]]] =
    for {
      socket <- RawDataSocket.connect(address, buffer = ByteBuffer.allocate(incomingBufferSize), group)
      response <- http(socket, address.getHostName, request)
    } yield response

  def http(socket: DataSocket[F, B], host: String, request: Request[Stream[F, B]]): F[Response[Stream[F, B]]] =
    for {
      requestStream <- http11.renderRequest(request.withHeader(Headers.Host, host))
      writeBytesFiber <- requestStream.foreach(socket.write).start // Write response asynchronously
      maybeResponse <- http11.decodeResponse(Decoder(socket.stream)).pull()
      response <-
        maybeResponse match {
          case Some(response) =>
            val (consumed, consumableBody) = response.body.handleConsumed
            consumed
              .after(writeBytesFiber.join())
              // Close socket when body was consumed
              // TODO socket connections should be recycled
              .after(socket.stream.cancel())
              .start
              .as(response.copy(body = consumableBody))
          case None =>
            Effect[F].fail[Response[Stream[F, B]]](
              new IllegalStateException("Peer has closed connection before sending response."))
        }
    } yield response

  def secureWebSocket(address: InetSocketAddress,
                      path: PathAndQuery,
                      outgoingFrames: Stream[F, WebSocketProtocol.Frame[B]],
                      cookie: Map[String, String],
                      headers: Map[String, String]): F[Response[Stream[F, Frame.Merged[B]]]] =
    for {
      rawSocket <- RawDataSocket.connect(address, buffer = ByteBuffer.allocate(incomingBufferSize), group)
      engine = sslContext.createSSLEngine(address.getHostName, address.getPort)
      socket <- SecureDataSocket.forClientMode(rawSocket, engine, blockingExecutor)
      frames <- webSocket(socket, address.getHostName, path, outgoingFrames, cookie, headers)
    } yield frames

  def webSocket(uri: URI,
                outgoingFrames: Stream[F, WebSocketProtocol.Frame[B]],
                cookie: Map[String, String],
                headers: Map[String, String]): F[Response[Stream[F, Frame.Merged[B]]]] = {
    val pq = pqFromUri(uri)
    val updatedHeaders =
      if (uri.getUserInfo != null) headers + Headers.basicAuthorization(uri.getUserInfo)
      else headers

    uri.getScheme match {
      case "ws" if uri.getPort == -1 => webSocket(new InetSocketAddress(uri.getHost, 80), pq, outgoingFrames, cookie, updatedHeaders)
      case "wss" if uri.getPort == -1 => secureWebSocket(new InetSocketAddress(uri.getHost, 443), pq, outgoingFrames, cookie, updatedHeaders)
      case "ws" =>  webSocket(new InetSocketAddress(uri.getHost, uri.getPort), pq, outgoingFrames, cookie, updatedHeaders)
      case "wss" => secureWebSocket(new InetSocketAddress(uri.getHost, uri.getPort), pq, outgoingFrames, cookie, updatedHeaders)
      case "http" | "https" => Effect[F].fail(new IllegalArgumentException(s"Use HttpClient.http() of HttpClient.webSocket()"))
      case scheme => Effect[F].fail(new IllegalArgumentException(s"$scheme is not supported"))
    }
  }

  def webSocket(address: InetSocketAddress,
                path: PathAndQuery,
                outgoingFrames: Stream[F, WebSocketProtocol.Frame[B]],
                cookie: Map[String, String],
                headers: Map[String, String]): F[Response[Stream[F, Frame.Merged[B]]]] =
    for {
      socket <- RawDataSocket.connect(address, buffer = ByteBuffer.allocate(incomingBufferSize), group)
      frames <- webSocket(socket, address.getHostName, path, outgoingFrames, cookie, headers)
    } yield frames

  def webSocket(socket: DataSocket[F, B],
                host: String,
                path: PathAndQuery,
                outgoingFrames: Stream[F, WebSocketProtocol.Frame[B]],
                cookie: Map[String, String],
                headers: Map[String, String]): F[Response[Stream[F, WebSocketProtocol.Frame.Merged[B]]]] = {
    val webSocketProtocol = new WebSocketProtocol[B]()
    for {
      intention <- WebSocketProtocol.Intention.random
      encodedOutgoingFrames = outgoingFrames.map(frame => webSocketProtocol.encodeFrame(frame, Some(123)))
      requestRaw = Request(Request.Method.Get, path, headers.toSeq, None, encodedOutgoingFrames)
      requestWithCookie = cookie.foldLeft(requestRaw) { case (acc, (k, v)) => acc.withCookie(k, v) }
      requestWithIntention = webSocketProtocol.addIntention(requestWithCookie, intention)
      rawResponse <- http(socket, host, requestWithIntention)
      frameDecoder = Decoder(rawResponse.body)
      frames = webSocketProtocol.mergeFrames(webSocketProtocol.decodeFrames(frameDecoder))
    } yield {
      rawResponse.copy(body = frames)
    }
  }

  private def pqFromUri(uri: URI) = {
    val path = if (uri.getPath == null) "" else uri.getPath
    val query = if (uri.getQuery == null) "" else uri.getQuery
    PathAndQuery.fromString(s"$path$query")
  }
}

object HttpClient {

  def create[F[_]: Effect, B: BytesLike](blockingExecutor: Executor = null,
                                         group: AsynchronousChannelGroup = null,
                                         incomingBufferSize: Int = 8096,
                                         sslContext: SSLContext = SSLContext.getDefault)
                                        (implicit executor: ExecutionContext): F[HttpClient[F, B]] = Effect[F].delay {
    val updatedExecutor =
      if (blockingExecutor != null) blockingExecutor
      else Executors.newCachedThreadPool()
    new HttpClient(updatedExecutor, group, incomingBufferSize, sslContext)
  }
}
