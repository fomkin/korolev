/*
 * Copyright 2017-2020 Aleksey Fomkin
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

package korolev.server.internal.services

import korolev.effect.syntax.*
import korolev.effect.{Effect, Queue, Reporter, Stream}
import korolev.internal.Frontend
import korolev.server.{HttpResponse, WebSocketResponse}
import korolev.server.internal.HttpResponse
import korolev.web.Request.Head
import korolev.web.Response
import korolev.web.Response.Status
import korolev.Qsid
import korolev.data.{Bytes, BytesLike}

import java.nio.{ByteBuffer, CharBuffer}
import java.nio.charset.StandardCharsets
import java.util.zip.{Deflater, Inflater}
import scala.collection.concurrent.TrieMap

private[korolev] final class MessagingService[F[_]: Effect](reporter: Reporter,
                                                            commonService: CommonService[F],
                                                            sessionsService: SessionsService[F, _, _]) {

  import MessagingService._

  /**
    * Poll message from session's ongoing queue.
    */
  def longPollingSubscribe(qsid: Qsid, rh: Head): F[HttpResponse[F]] = {
    for {
      _ <- sessionsService.createAppIfNeeded(qsid, rh, createTopic(qsid))
      maybeApp <- sessionsService.getApp(qsid)
      // See webSocketMessaging()
      maybeMessage <- maybeApp.fold(SomeReloadMessageF)(_.frontend.outgoingMessages.pull())
      response <- maybeMessage match {
        case None => Effect[F].pure(commonGoneResponse)
        case Some(message) =>
          HttpResponse(
            status = Response.Status.Ok,
            message = message,
            headers = commonResponseHeaders
          )
      }
    } yield {
      response
    }
  }

  /**
    * Push message to session's incoming queue.
    */
  def longPollingPublish(qsid: Qsid, data: Stream[F, Bytes]): F[HttpResponse[F]] = {
    for {
      topic <- takeTopic(qsid)
      message <- data.fold(Bytes.empty)(_ ++ _).map(_.asUtf8String)
      _ <- topic.enqueue(message)
    } yield commonOkResponse
  }

  private lazy val inflaters = ThreadLocal.withInitial(() => new Inflater(true))
  private lazy val deflaters = ThreadLocal.withInitial(() => new Deflater(Deflater.DEFAULT_COMPRESSION, true))
  // TODO this buffers is to huge and the may be not enough anyway.
  private lazy val compressionInputBuffers = ThreadLocal.withInitial(() => ByteBuffer.allocate(1024 * 1024 * 10)) // 10M
  private lazy val compressionOutputBuffers = ThreadLocal.withInitial(() => ByteBuffer.allocate(1024 * 1024 * 10)) // 10M

  private val wsJsonDeflateDecoder = (bytes: Bytes) => {
    val inputBuffer = compressionInputBuffers.get()
    val outputBuffer = compressionOutputBuffers.get()
    val inflater = inflaters.get()
    inputBuffer.clear()
    outputBuffer.clear()
    inflater.reset()
    bytes.copyToBuffer(inputBuffer)
    inputBuffer.flip()
    inflater.setInput(inputBuffer)
    inflater.inflate(outputBuffer)
    outputBuffer.flip()
    StandardCharsets.UTF_8.decode(outputBuffer).toString
  }

  private val wsJsonDeflateEncoder = (message: String) => {
    val encoder = StandardCharsets.UTF_8.newEncoder()
    val inputBuffer = compressionInputBuffers.get()
    val outputBuffer = compressionOutputBuffers.get()
    val deflatter = deflaters.get()
    // Cleanup
    inputBuffer.clear()
    outputBuffer.clear()
    deflatter.reset()
    //
    val chars = CharBuffer.wrap(message)
    encoder.encode(chars, inputBuffer, true)
    inputBuffer.flip()
    deflatter.setInput(inputBuffer)
    deflatter.finish()
    deflatter.deflate(outputBuffer, Deflater.SYNC_FLUSH)
    outputBuffer.flip()
    val array = new Array[Byte](outputBuffer.remaining())
    outputBuffer.get(array)
    Bytes.wrap(array)

//    deflatter.reset()
//    deflatter.setInput(inputBuffer)
//    val chars = CharBuffer.wrap(message)
//    var result = Bytes.empty
//    while (chars.remaining() > 0) {
//      inputBuffer.clear()
//      outputBuffer.clear()
//      encoder.encode(chars, inputBuffer, false)
//      inputBuffer.flip()
//      deflatter.deflate(outputBuffer)
//      outputBuffer.flip()
//      val array = new Array[Byte](outputBuffer.remaining())
//      outputBuffer.get(array)
//      result = result ++ Bytes.wrap(array)
//    }
//    deflatter.finish()
//    result

  }

  private val wsJsonDecoder = (bytes: Bytes) => bytes.asUtf8String
  private val wsJsonEncoder = (message: String) => BytesLike[Bytes].utf8(message)

  def webSocketMessaging(qsid: Qsid, rh: Head, incomingMessages: Stream[F, Bytes], protocols: Seq[String]): F[WebSocketResponse[F]] = {
    val (selectedProtocol, decoder, encoder) = {
      // Support for protocol compression. A client can tell us
      // it can decompress the messages.
      if (protocols.contains(ProtocolJsonDeflate)) {
        (ProtocolJsonDeflate, wsJsonDeflateDecoder, wsJsonDeflateEncoder)
      } else {
        (ProtocolJson, wsJsonDecoder, wsJsonEncoder)
      }
    }
    sessionsService.createAppIfNeeded(qsid, rh, incomingMessages.map(decoder)) flatMap { _ =>
      sessionsService.getApp(qsid) flatMap  {
        case Some(app) =>
          val httpResponse = Response(Status.Ok, app.frontend.outgoingMessages.map(encoder), Nil, None)
          Effect[F].pure(WebSocketResponse(httpResponse, selectedProtocol))
        case None =>
          // Respond with reload message because app was not found.
          // In this case it means that server had ben restarted and
          // do not have an information about the state which had been
          // applied to render of the page on a client side.
          Stream(Frontend.ReloadMessage).mat().map { messages =>
            val httpResponse = Response(Status.Ok, messages.map(encoder), Nil, None)
            WebSocketResponse(httpResponse, selectedProtocol)
          }
      }
    }
  }

  /**
    * Sessions created via long polling subscription
    * takes messages from topics stored in this table.
    */
  private val longPollingTopics = TrieMap.empty[Qsid, Queue[F, String]]

  /**
    * Same headers in all responses
    */
  private val commonResponseHeaders = Seq(
    "cache-control" -> "no-cache",
    "content-type" -> "application/json"
  )

  /**
    * Same response for all 'publish' requests.
    */
  private val commonOkResponse = Response(
    status = Response.Status.Ok,
    body = Stream.empty[F, Bytes],
    headers = commonResponseHeaders,
    contentLength = Some(0L)
  )

  /**
    * Same response for all 'subscribe' requests
    * where outgoing stream is consumed.
    */
  private val commonGoneResponse = Response(
    status = Response.Status.Gone,
    body = Stream.empty[F, Bytes],
    headers = commonResponseHeaders,
    contentLength = Some(0L)
  )

  private def takeTopic(qsid: Qsid) =
    Effect[F].delay {
      if (longPollingTopics.contains(qsid)) longPollingTopics(qsid)
      else throw new Exception(s"There is no long-polling topic matching $qsid")
    }

  private def createTopic(qsid: Qsid) = {
    reporter.debug(s"Create long-polling topic for $qsid")
    val topic = Queue[F, String]()
    topic.cancelSignal.runAsync(_ => longPollingTopics.remove(qsid))
    longPollingTopics.putIfAbsent(qsid, topic)
    topic.stream
  }
}

private[korolev] object MessagingService {

  private val ProtocolJsonDeflate = "json-deflate"
  private val ProtocolJson = "json"

  def SomeReloadMessageF[F[_]: Effect]: F[Option[String]] =
    Effect[F].pure(Option(Frontend.ReloadMessage))
}