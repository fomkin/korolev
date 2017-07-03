package korolev.blazeServer


import java.nio.charset.StandardCharsets

import org.http4s.blaze.pipeline.{Command => Cmd, _}
import org.http4s.blaze.util.Execution._
import org.http4s.websocket.WebsocketBits.WebSocketFrame
import org.http4s.websocket.WebsocketHandshake

import scala.util.control.NonFatal
import scala.util.{Failure, Success}
import scala.concurrent.Future
import scala.collection.mutable.ArrayBuffer
import org.http4s.blaze.http.http_parser.Http1ServerParser
import org.http4s.blaze.http.http_parser.BaseExceptions.BadRequest
import org.http4s.blaze.http.websocket.WebSocketDecoder
import java.util.Date
import java.nio.ByteBuffer

import org.http4s.blaze.http.{Headers, HttpResponse, HttpService, WSResponse}
import org.http4s.blaze.util.BufferTools

class HttpServerStage(maxReqBody: Long, maxNonbody: Int)(handleRequest: HttpService)
  extends Http1ServerParser(maxNonbody, maxNonbody, 5*1024) with TailStage[ByteBuffer] {
  import HttpServerStage._

  private implicit def ec = trampoline

  val name = "HTTP/1.1_Stage"

  private var uri: String = null
  private var method: String = null
  private var minor: Int = -1
  private var major: Int = -1
  private var headers = new ArrayBuffer[(String, String)]

  /////////////////////////////////////////////////////////////////////////////////////////

  // Will act as our loop
  override def stageStartup() {
    logger.info("Starting HttpStage")
    requestLoop()
  }

  private def requestLoop(): Unit = {
    channelRead().onComplete {
      case Success(buff)    => readLoop(buff)
      case Failure(Cmd.EOF) => // NOOP
      case Failure(t)       => shutdownWithCommand(Cmd.Error(t))
    }
  }

  private def readLoop(buff: ByteBuffer): Unit = {
    try {
      if (!requestLineComplete() && !parseRequestLine(buff)) {
        requestLoop()
        return
      }

      if (!headersComplete() && !parseHeaders(buff)) {
        requestLoop()
        return
      }

      // TODO: need to check if we need a Host header or otherwise validate the request
      // we have enough to start the request
      gatherBody(buff, 0, new ArrayBuffer[ByteBuffer])
    }
    catch { case t: Throwable => shutdownWithCommand(Cmd.Error(t)) }
  }

  private def resetStage() {
    reset()
    uri = null
    method = null
    minor = -1
    major = -1
    headers.clear()
  }

  private def runRequest(buffer: ByteBuffer, reqHeaders: Headers): Unit = {
    try handleRequest(method, uri, reqHeaders, buffer).flatMap {
      case r: HttpResponse    => handleHttpResponse(r, reqHeaders, false)
      case WSResponse(stage)        => handleWebSocket(reqHeaders, stage)
    }.onComplete {       // See if we should restart the loop
      case Success(Reload)          => resetStage(); requestLoop()
      case Success(Close)           => shutdownWithCommand(Cmd.Disconnect)
      case Success(Upgrade)         => // NOOP don't need to do anything
      case Failure(t: BadRequest)   => badRequest(t)
      case Failure(t)               => shutdownWithCommand(Cmd.Error(t))
    }
    catch {
      case NonFatal(e) =>
        logger.error(e)("Error during `handleRequest` of HttpServerStage")
        val body = ByteBuffer.wrap("Internal Service Error".getBytes(StandardCharsets.ISO_8859_1))
        val resp = HttpResponse(200, "OK", Nil, body)

        handleHttpResponse(resp, reqHeaders, false).onComplete { _ =>
          shutdownWithCommand(Cmd.Error(e))
        }
    }
  }

  /** Deal with route responses of standard HTTP form */
  private def handleHttpResponse(resp: HttpResponse, reqHeaders: Headers, forceClose: Boolean): Future[RouteResult] = {
    val sb = new StringBuilder(512)
    sb.append("HTTP/").append(1).append('.').append(minor).append(' ')
      .append(resp.code).append(' ')
      .append(resp.status).append('\r').append('\n')

    val keepAlive = !forceClose && isKeepAlive(reqHeaders)

    if (!keepAlive) sb.append("Connection: close\r\n")
    else if (minor == 0 && keepAlive) sb.append("Connection: Keep-Alive\r\n")

    renderHeaders(sb, resp.headers, resp.body.remaining())

    val messages = Array(ByteBuffer.wrap(sb.result().getBytes(StandardCharsets.ISO_8859_1)), resp.body)

    channelWrite(messages).map(_ => if (keepAlive) Reload else Close)(directec)
  }

  /** Deal with route response of WebSocket form */
  private def handleWebSocket(reqHeaders: Headers, wsBuilder: LeafBuilder[WebSocketFrame]): Future[RouteResult] = {
    val sb = new StringBuilder(512)
    WebsocketHandshake.serverHandshake(reqHeaders) match {
      case Left((i, msg)) =>
        logger.info(s"Invalid handshake: $i: $msg")
        sb.append("HTTP/1.1 ").append(i).append(' ').append(msg).append('\r').append('\n')
          .append('\r').append('\n')

        channelWrite(ByteBuffer.wrap(sb.result().getBytes(StandardCharsets.ISO_8859_1))).map(_ => Close)

      case Right(hdrs) =>
        logger.info("Starting websocket request")
        sb.append("HTTP/1.1 101 Switching Protocols\r\n")
        hdrs.foreach { case (k, v) => sb.append(k).append(": ").append(v).append('\r').append('\n') }
        sb.append('\r').append('\n')

        // write the accept headers and reform the pipeline
        channelWrite(ByteBuffer.wrap(sb.result().getBytes(StandardCharsets.ISO_8859_1))).map{ _ =>
          logger.debug("Switching pipeline segments for upgrade")
          val segment = wsBuilder.prepend(new WebSocketDecoder(false))
          this.replaceInline(segment)
          Upgrade
        }
    }
  }

  private def badRequest(msg: BadRequest): Unit = {
    val sb = new StringBuilder(512)
    sb.append("HTTP/").append(1).append('.')
      .append(minor).append(' ').append(400)
      .append(" Bad Request\r\n\r\n")

    channelWrite(ByteBuffer.wrap(sb.result().getBytes(StandardCharsets.ISO_8859_1)))
      .onComplete(_ => shutdownWithCommand(Cmd.Disconnect))
  }

  private def renderHeaders(sb: StringBuilder, headers: Seq[(String, String)], length: Int) {
    headers.foreach { case (k, v) =>
      // We are not allowing chunked responses at the moment, strip our Chunked-Encoding headers
      if (!k.equalsIgnoreCase("Transfer-Encoding") && !k.equalsIgnoreCase("Content-Length")) {
        sb.append(k)
        if (v.length > 0) sb.append(": ").append(v).append('\r').append('\n')
      }
    }
    // Add our length header last
    sb.append(s"Content-Length: ").append(length).append('\r').append('\n')
    sb.append('\r').append('\n')
  }

  // TODO: this will generate a long chain of Futures
  private def gatherBody(buffer: ByteBuffer, bodySize: Long, buffers: ArrayBuffer[ByteBuffer]): Unit = {
    def finalizeBody(): Unit = {
      val total = BufferTools.joinBuffers(buffers)
      val hs = headers
      headers = new ArrayBuffer[(String, String)](hs.size + 10)
      runRequest(total, hs)
    }
    def bodyReadLoop(totalBodySize: Long) = {
      channelRead().onComplete {
        case Success(newbuff) => gatherBody(BufferTools.concatBuffers(buffer, newbuff), totalBodySize, buffers)
        case Failure(Cmd.EOF) => stageShutdown(); sendOutboundCommand(Cmd.Disconnect)
        case Failure(t)       => shutdownWithCommand(Cmd.Error(t))
      }
    }
    if (!contentComplete()) {
      val buff = parseContent(buffer)
      if (buff == null) {
        bodyReadLoop(0)
      } else {
        val totalBodySize = bodySize + buff.remaining()
        if (maxReqBody > 0 && totalBodySize > maxReqBody) {
          val hs = headers
          headers = new ArrayBuffer[(String, String)](0)
          handleHttpResponse(HttpResponse.EntityTooLarge(), hs, false)
        }
        else {
          buffers += buff
          if (contentComplete()) finalizeBody()
          else bodyReadLoop(totalBodySize)
        }
      }
    }
    else finalizeBody()
  }

  private def shutdownWithCommand(cmd: Cmd.OutboundCommand): Unit = {
    stageShutdown()
    sendOutboundCommand(cmd)
  }

  private def isKeepAlive(headers: Headers): Boolean = {
    val h = headers.find {
      case (k, _) if k.equalsIgnoreCase("connection") => true
      case _                                          => false
    }

    h match {
      case Some((k, v)) =>
        if (v.equalsIgnoreCase("Keep-Alive")) true
        else if (v.equalsIgnoreCase("close")) false
        else if (v.equalsIgnoreCase("Upgrade")) true
        else {
          logger.info(s"Bad Connection header value: '$v'. Closing after request.")
          false
        }

      case None if minor == 0 => false
      case None               => true
    }
  }

  override protected def stageShutdown(): Unit = {
    logger.info("Shutting down HttpPipeline at " + new Date())
    shutdownParser()
  }

  protected def headerComplete(name: String, value: String): Boolean = {
    headers += ((name, value))
    false
  }

  protected def submitRequestLine(methodString: String,
    uri: String,
    scheme: String,
    majorversion: Int,
    minorversion: Int): Boolean = {
    this.uri = uri
    this.method = methodString
    this.major = majorversion
    this.minor = minorversion
    false
  }
}

private object HttpServerStage {
  sealed trait RouteResult
  case object Reload  extends RouteResult
  case object Close   extends RouteResult
  case object Upgrade extends RouteResult
}
