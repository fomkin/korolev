package korolev.http.protocol

import korolev.data.ByteVector
import korolev.effect.syntax._
import korolev.effect.{Decoder, Effect, Stream}
import korolev.web.Response.Status
import korolev.web.{Headers, Path, Request, Response}

import scala.collection.mutable

object Http11 {

  private final val LastChunk = Stream(ByteVector.ascii("0\r\n\r\n"))

  private final val HeaderDelimiter =
    Array[Byte]('\r', '\n', '\r', '\n')

  private final val HeaderDelimiterBv =
    ByteVector.ascii("\r\n\r\n")

  private implicit final class StringBuilderOps(val builder: StringBuilder) extends AnyVal {
    def newLine(): StringBuilder = builder
      .append('\r')
      .append('\n')
  }

  private def printDebug(bytes: ByteVector, prefix: String) = println(
    prefix + bytes
      .utf8String
      .replaceAll("\r", s"${Console.RESET}${Console.YELLOW_B}\\\\r${Console.RESET}${Console.YELLOW}")
      .replaceAll("\n", s"${Console.RESET}${Console.YELLOW_B}\\\\n${Console.RESET}${Console.YELLOW}\n$prefix")
  )

  def decodeRequest[F[_]: Effect](decoder: Decoder[F, ByteVector]): Stream[F, Request[Stream[F, ByteVector]]] = decoder
      .decode(ByteVector.empty)(decodeRequest)
      .map { request =>
        request.copy(
          body = request.contentLength match {
            case Some(contentLength) =>
              decoder.decode(0L)(decodeLimitedBody(_, _, contentLength))
            case None =>
              decoder
          }
        )
      }

  def decodeRequest(buffer: ByteVector,
                    incoming: ByteVector): (ByteVector, Decoder.Action[ByteVector, Request[Unit]]) = {
    val allBytes = buffer ++ incoming
    findLastHeaderEnd(allBytes) match {
      case -1 => (allBytes, Decoder.Action.TakeNext)
      case lastByteOfHeader =>
        val (bodyBytes, request) = parseRequest(allBytes, lastByteOfHeader)
        (ByteVector.empty, Decoder.Action.Fork(request, bodyBytes))
    }
  }

  def decodeResponse[F[_]: Effect](decoder: Decoder[F, ByteVector]): Stream[F, Response[Stream[F, ByteVector]]] = decoder
    .decode(ByteVector.empty)(decodeResponse)
    .map { response =>
      response.copy(
        body = response.contentLength match {
          case Some(contentLength) =>
            decoder.decode(0L)(decodeLimitedBody(_, _, contentLength))
          case None if response.header(Headers.TransferEncoding).contains("chunked") =>
            decoder.decode((Option.empty[Long], ByteVector.empty))(decodeChunkedBody)
          case None =>
            decoder
        }
      )
    }

  def decodeResponse(buffer: ByteVector,
                     incoming: ByteVector): (ByteVector, Decoder.Action[ByteVector, Response[Unit]]) = {
    val allBytes = buffer ++ incoming
    findLastHeaderEnd(allBytes) match {
      case -1 =>
        (allBytes, Decoder.Action.TakeNext)
      case lastByteOfHeader =>
        val (bodyBytes, response) = parseResponse(allBytes, lastByteOfHeader)
        (ByteVector.empty, Decoder.Action.Fork(response, bodyBytes))
    }
  }

  def decodeLimitedBody[F[_] : Effect](prevBytesTotal: Long,
                                       incoming: ByteVector,
                                       contentLength: Long): (Long, Decoder.Action[ByteVector, ByteVector]) =
    prevBytesTotal + incoming.length match {
      case `contentLength` =>
        (contentLength, Decoder.Action.PushFinish(incoming))
      case total if total > contentLength =>
        val pos = contentLength - prevBytesTotal
        val value = incoming.slice(0, pos)
        val takeBack = incoming.slice(pos)
        // Push left slice to a downstream
        // and take back right slice to the upstream
        (total, Decoder.Action.ForkFinish(value, takeBack))
      case total =>
        (total, Decoder.Action.Push(incoming))
    }

  def decodeChunkedBody(state: (Option[Long], ByteVector),
                        incoming: ByteVector): ((Option[Long], ByteVector), Decoder.Action[ByteVector, ByteVector]) =
    state match {
      case (maybeChunkSize @ Some(chunkSize), buffer) =>
        val allBytes = buffer ++ incoming
        val expectedLength = chunkSize + ByteVector.CRLF.length
        if (allBytes.length >= expectedLength) {
          if (chunkSize > 0) {
            val chunk = allBytes.slice(0, chunkSize)
            val rest = allBytes.slice(expectedLength)
            ((None, ByteVector.empty), Decoder.Action.Fork(chunk, rest))
          } else {
            val rest = allBytes.slice(expectedLength)
            ((None, ByteVector.empty), Decoder.Action.TakeBackFinish(rest))
          }
        } else {
          ((maybeChunkSize, allBytes), Decoder.Action.TakeNext)
        }
      case (None, buffer) =>
        val allBytes = buffer ++ incoming
        val index = allBytes.indexOfThat(ByteVector.CRLF)
        if (index < 0) {
          ((None, allBytes), Decoder.Action.TakeNext)
        } else {
          val chunkSizeHex = allBytes.slice(0, index).asciiString
          val chunkSize = java.lang.Long.parseLong(chunkSizeHex, 16)
          val restBytes = allBytes.slice(index + ByteVector.CRLF.length)
          ((Some(chunkSize), ByteVector.empty), Decoder.Action.TakeBack(restBytes))
        }
    }

  def parseRequest(allBytes: ByteVector,
                   lastByteOfHeader: Long): (ByteVector, Request[Unit]) = {
    // Buffer contains header.
    // Lets parse it.
    val methodEnd = allBytes.indexOf(' ')
    val paramsStart = allBytes.indexOf('?', methodEnd + 1)
    val pathEnd = allBytes.indexOf(' ', methodEnd + 1)
    val protocolVersionEnd = allBytes.indexOf('\r')
    val hasParams = paramsStart > -1 && paramsStart < protocolVersionEnd
    val method = allBytes.slice(0, methodEnd).asciiString
    val path = allBytes.slice(methodEnd + 1, if (hasParams) paramsStart else pathEnd).asciiString
    val params =
      if (hasParams) allBytes.slice(paramsStart + 1, pathEnd).asciiString
      else null
    //val protocolVersion = allBytes.slice(pathEnd + 1, protocolVersionEnd).asciiString
    // Parse headers.
    val headers = mutable.Buffer.empty[(String, String)]
    var headerStart = protocolVersionEnd + 2 // first line end plus \r\n chars
    var cookie: String = null
    var contentLength: String = null
    while (headerStart < lastByteOfHeader) {
      val nameEnd = allBytes.indexOf(':', headerStart)
      val valueEnd = allBytes.indexOf('\r', nameEnd)
      val name = allBytes.slice(headerStart, nameEnd).asciiString
      val value =
        if (allBytes(nameEnd + 1) == ' ') allBytes.slice(nameEnd + 2, valueEnd).asciiString
        else allBytes.slice(nameEnd + 1, valueEnd).asciiString
      name match {
        case _ if name.equalsIgnoreCase(Headers.Cookie) => cookie = value
        case _ if name.equalsIgnoreCase(Headers.ContentLength) => contentLength = value
        case _ => headers += ((name, value))
      }
      headerStart = valueEnd + 2
    }
    val request = Request(
      method = Request.Method.fromString(method),
      path = Path.fromString(path),
      renderedParams = params,
      renderedCookie = cookie,
      headers = headers.toVector,
      body = (),
      contentLength =
        if (contentLength == null) None
        else Some(contentLength.toLong)
    )
    val bodyBytes = allBytes.slice(lastByteOfHeader + 4, allBytes.length)
    (bodyBytes, request)
  }

  def findLastHeaderEnd(bytes: ByteVector): Long =
    bytes.indexOfThat(HeaderDelimiterBv)

  def parseResponse(bytes: ByteVector, lastHeaderEnd: Long): (ByteVector, Response[Unit]) = {
    // First line
    val protocolVersionEnd = bytes.indexOf(' ')
    val statusCodeStart = protocolVersionEnd + 1
    val statusCodeEnd = bytes.indexOf(' ', statusCodeStart)
    val statusPhraseStart = statusCodeEnd + 1
    val statusPhraseEnd = bytes.indexOf('\r', statusPhraseStart)
    val statusCode = bytes.slice(statusCodeStart, statusCodeEnd)
    val statusPhrase = bytes.slice(statusPhraseStart, statusPhraseEnd)
    // Headers
    var headerStart = statusPhraseEnd + 2
    val headers = mutable.Buffer.empty[(String, String)]
    var cookie: String = null
    var contentLength: String = null
    while (headerStart < lastHeaderEnd) {
      val nameEnd = bytes.indexOf(':', headerStart)
      val valueEnd = bytes.indexOf('\r', nameEnd)
      val name = bytes.slice(headerStart, nameEnd).asciiString
      val value =
        if (bytes(nameEnd + 1) == ' ') bytes.slice(nameEnd + 2, valueEnd).asciiString
        else bytes.slice(nameEnd + 1, valueEnd).asciiString
      name match {
        case _ if name.equalsIgnoreCase(Headers.Cookie) =>
          cookie = value
          headers += ((name, value))
        case _ if name.equalsIgnoreCase(Headers.ContentLength) =>
          contentLength = value
        case _ =>
          headers += ((name, value))
      }
      headerStart = valueEnd + 2
    }
    val response = Response(
      status = Status(statusCode.asciiString.toInt, statusPhrase.asciiString),
      headers = headers.toVector,
      contentLength =
        if (contentLength == null) None
        else Some(contentLength.toLong),
      body = ()
    )
    val bodyBytes = bytes.slice(lastHeaderEnd + 4, bytes.length)
    (bodyBytes, response)
  }

  def renderResponseHeader(status: Status, headers: Seq[(String, String)]): String = {
    val builder = new StringBuilder()
    builder.append("HTTP/1.1 ")
      .append(status.codeAsString)
      .append(' ')
      .append(status.phrase)
      .newLine()
    def putHeader(name: String, value: String) = builder
      .append(name)
      .append(':')
      .append(' ')
      .append(value)
      .newLine()
    headers.foreach {
      case (name, value) =>
        putHeader(name, value)
    }
    builder
      .newLine()
      .mkString
  }

  def renderRequestHead[T](request: Request[T]): String = {
    val sb = new StringBuilder()
      .append(request.method.value)
      .append(' ')
      .append(request.path.mkString)
    if (request.renderedParams != null && request.renderedParams.nonEmpty) sb
      .append('?')
      .append(request.renderedParams)
    sb
      .append(' ')
      .append("HTTP/1.1")
      .newLine()
    if (request.renderedCookie != null && request.renderedCookie.nonEmpty) sb
      .append(Headers.Cookie)
      .append(": ")
      .append(request.renderedCookie)
      .newLine()
    request.contentLength match {
      case None => ()
      case Some(contentLength) => sb
        .append(Headers.ContentLength)
        .append(": ")
        .append(contentLength)
        .newLine()
    }
    request.headers.foreach {
      case (k, v) => sb
        .append(k)
        .append(": ")
        .append(v)
        .newLine()
    }
    sb
      .newLine()
      .mkString
  }
  def renderRequest[F[_]: Effect](request: Request[Stream[F, ByteVector]]): F[Stream[F, ByteVector]] = {
    val head = renderRequestHead(request)
    Stream(ByteVector.ascii(head))
      .mat()
      .map(_ ++ request.body) // TODO add chunked encoding
  }

  def renderResponse[F[_]: Effect](response: Response[Stream[F, ByteVector]]): F[Stream[F, ByteVector]] = {
    def go(readers: Seq[(String, String)], body: Stream[F, ByteVector]) = {
      val fullHeaderString =  renderResponseHeader(response.status, readers)
      val fullHeaderBytes = ByteVector.ascii(fullHeaderString)
      Stream(fullHeaderBytes).mat().map(_ ++ body)
    }
    response.contentLength match {
      case Some(s) => go((Headers.ContentLength -> s.toString) +: response.headers, response.body)
      case None if response.status == Status.SwitchingProtocols => go(response.headers, response.body)
      case None =>
        val chunkedBody = response.body.map { chunk =>
          ByteVector.ascii(chunk.length.toHexString) ++
            ByteVector.CRLF ++
            chunk ++
            ByteVector.CRLF
        }
        LastChunk
          .mat()
          .flatMap { lastChunk =>
            go(Headers.TransferEncodingChunked +: response.headers, chunkedBody ++ lastChunk)
          }
    }
  }
}
