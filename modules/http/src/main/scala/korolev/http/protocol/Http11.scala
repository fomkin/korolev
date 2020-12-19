package korolev.http.protocol

import korolev.data.BytesLike
import korolev.data.syntax._
import korolev.effect.syntax._
import korolev.effect.{Decoder, Effect, Stream}
import korolev.web.Response.Status
import korolev.web.{Headers, PathAndQuery, Request, Response}

import scala.collection.mutable

class Http11[B: BytesLike] {

  import Http11._

  private final val LastChunk = Stream(BytesLike[B].ascii("0\r\n\r\n"))
  private final val HeaderDelimiter = BytesLike[B].ascii("\r\n\r\n")
  private final val CRLF = BytesLike[B].ascii("\r\n")

//  private def printDebug(bytes: ByteVector, prefix: String): Unit = println(
//    prefix + bytes
//      .utf8String
//      .replaceAll("\r", s"${Console.RESET}${Console.YELLOW_B}\\\\r${Console.RESET}${Console.YELLOW}")
//      .replaceAll("\n", s"${Console.RESET}${Console.YELLOW_B}\\\\n${Console.RESET}${Console.YELLOW}\n$prefix")
//  )

  def decodeRequest[F[_]: Effect](decoder: Decoder[F, B]): Stream[F, Request[Stream[F, B]]] = decoder
      .decode(BytesLike[B].empty)(decodeRequest)
      .map { request =>
        request.copy(
          body = request.contentLength match {
            case Some(0) =>
              Stream.empty
            case Some(contentLength) =>
              decoder.decode(0L)(decodeLimitedBody(_, _, contentLength))
            case None if request.header(Headers.TransferEncoding).contains("chunked") =>
              decoder.decode((Option.empty[Long], BytesLike[B].empty))(decodeChunkedBody)
            case None =>
              decoder
          }
        )
      }

  def decodeRequest(buffer: B,
                    incoming: B): (B, Decoder.Action[B, Request[Unit]]) = {
    val allBytes = buffer ++ incoming
    findLastHeaderEnd(allBytes) match {
      case -1 => (allBytes, Decoder.Action.TakeNext)
      case lastByteOfHeader =>
        val (bodyBytes, request) = parseRequest(allBytes, lastByteOfHeader)
        (BytesLike[B].empty, Decoder.Action.Fork(request, bodyBytes))
    }
  }

  def decodeResponse[F[_]: Effect](decoder: Decoder[F, B]): Stream[F, Response[Stream[F, B]]] = decoder
    .decode(BytesLike[B].empty)(decodeResponse)
    .map { response =>
      response.copy(
        body = response.contentLength match {
          case Some(contentLength) =>
            decoder.decode(0L)(decodeLimitedBody(_, _, contentLength))
          case None if response.header(Headers.TransferEncoding).contains("chunked") =>
            decoder.decode((Option.empty[Long], BytesLike[B].empty))(decodeChunkedBody)
          case None =>
            decoder
        }
      )
    }

  def decodeResponse(buffer: B,
                     incoming: B): (B, Decoder.Action[B, Response[Unit]]) = {
    val allBytes = buffer ++ incoming
    findLastHeaderEnd(allBytes) match {
      case -1 =>
        (allBytes, Decoder.Action.TakeNext)
      case lastByteOfHeader =>
        val (bodyBytes, response) = parseResponse(allBytes, lastByteOfHeader)
        (BytesLike[B].empty, Decoder.Action.Fork(response, bodyBytes))
    }
  }

  def decodeLimitedBody[F[_] : Effect](prevBytesTotal: Long,
                                       incoming: B,
                                       contentLength: Long): (Long, Decoder.Action[B, B]) =
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

  def decodeChunkedBody(state: (Option[Long], B),
                        incoming: B): ((Option[Long], B), Decoder.Action[B, B]) =
    state match {
      case (maybeChunkSize @ Some(chunkSize), buffer) =>
        val allBytes = buffer ++ incoming
        val expectedLength = chunkSize + CRLF.length
        if (allBytes.length >= expectedLength) {
          if (chunkSize > 0) {
            val chunk = allBytes.slice(0, chunkSize)
            val rest = allBytes.slice(expectedLength)
            ((None, BytesLike[B].empty), Decoder.Action.Fork(chunk, rest))
          } else {
            val rest = allBytes.slice(expectedLength)
            ((None, BytesLike[B].empty), Decoder.Action.TakeBackFinish(rest))
          }
        } else {
          ((maybeChunkSize, allBytes), Decoder.Action.TakeNext)
        }
      case (None, buffer) =>
        val allBytes = buffer ++ incoming
        val index = allBytes.indexOfSlice(CRLF)
        if (index < 0) {
          ((None, allBytes), Decoder.Action.TakeNext)
        } else {
          val chunkSizeHex = allBytes.slice(0, index).asAsciiString
          val chunkSize = java.lang.Long.parseLong(chunkSizeHex, 16)
          val restBytes = allBytes.slice(index + CRLF.length)
          ((Some(chunkSize), BytesLike[B].empty), Decoder.Action.TakeBack(restBytes))
        }
    }

  def parseRequest(allBytes: B,
                   lastByteOfHeader: Long): (B, Request[Unit]) = {
    // Buffer contains header.
    // Lets parse it.
    val methodEnd = allBytes.indexOf(' ')
    val paramsStart = allBytes.indexOf('?', methodEnd + 1)
    val pathEnd = allBytes.indexOf(' ', methodEnd + 1)
    val protocolVersionEnd = allBytes.indexOf('\r')
    val hasParams = paramsStart > -1 && paramsStart < protocolVersionEnd
    val method = allBytes.slice(0, methodEnd).asAsciiString
    val path = allBytes.slice(methodEnd + 1, if (hasParams) paramsStart else pathEnd).asAsciiString
    val params =
      if (hasParams) allBytes.slice(paramsStart + 1, pathEnd).asAsciiString
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
      val name = allBytes.slice(headerStart, nameEnd).asAsciiString
      val value =
        if (allBytes(nameEnd + 1) == ' ') allBytes.slice(nameEnd + 2, valueEnd).asAsciiString
        else allBytes.slice(nameEnd + 1, valueEnd).asAsciiString
      name match {
        case _ if name.equalsIgnoreCase(Headers.Cookie) => cookie = value
        case _ if name.equalsIgnoreCase(Headers.ContentLength) => contentLength = value
        case _ => headers += ((name, value))
      }
      headerStart = valueEnd + 2
    }
    val request = Request(
      method = Request.Method.fromString(method),
      pq = PathAndQuery.fromString(path).withParams(Option(params)),
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

  def findLastHeaderEnd(bytes: B): Long =
    bytes.indexOfSlice(HeaderDelimiter)

  def parseResponse(bytes: B, lastHeaderEnd: Long): (B, Response[Unit]) = {
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
      val name = bytes.slice(headerStart, nameEnd).asAsciiString
      val value =
        if (bytes(nameEnd + 1) == ' ') bytes.slice(nameEnd + 2, valueEnd).asAsciiString
        else bytes.slice(nameEnd + 1, valueEnd).asAsciiString
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
      status = Status(statusCode.asAsciiString.toInt, statusPhrase.asAsciiString),
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
      .append(request.pq.mkString)

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
  def renderRequest[F[_]: Effect](request: Request[Stream[F, B]]): F[Stream[F, B]] = {
    val head = renderRequestHead(request)
    Stream(BytesLike[B].ascii(head))
      .mat()
      .map(_ ++ request.body) // TODO add chunked encoding
  }

  def renderResponse[F[_]: Effect](response: Response[Stream[F, B]]): F[Stream[F, B]] = {
    def go(readers: Seq[(String, String)], body: Stream[F, B]) = {
      val fullHeaderString =  renderResponseHeader(response.status, readers)
      val fullHeaderBytes = BytesLike[B].ascii(fullHeaderString)
      Stream(fullHeaderBytes).mat().map(_ ++ body)
    }
    response.contentLength match {
      case Some(s) => go((Headers.ContentLength -> s.toString) +: response.headers, response.body)
      case None if response.status == Status.SwitchingProtocols => go(response.headers, response.body)
      case None if response.header(Headers.TransferEncoding).contains("chunked") =>
        val chunkedBody = response.body.map { chunk =>
          BytesLike[B].ascii(chunk.length.toHexString) ++
            CRLF ++
            chunk ++
            CRLF
        }
        LastChunk
          .mat()
          .flatMap { lastChunk =>
            go(response.headers, chunkedBody ++ lastChunk)
          }
      case None =>
        println("NOT CHUNKED BUT NONE")
        response.headers.foreach(println)
        Effect[F].pure(Stream.empty)
    }
  }
}

object Http11 {
  implicit final class StringBuilderOps(val builder: StringBuilder) extends AnyVal {
    def newLine(): StringBuilder = builder
      .append('\r')
      .append('\n')
  }
}