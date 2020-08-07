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

import korolev.effect.syntax._
import korolev.effect.{Effect, Queue, Reporter, Stream}
import korolev.server.Request.RequestHeader
import korolev.server.Response
import korolev.server.Response.Status
import korolev.Qsid
import korolev.effect.io.LazyBytes
import korolev.internal.Frontend
import korolev.internal.Frontend.ReloadMessage

import scala.collection.mutable

private[korolev] final class MessagingService[F[_]: Effect](reporter: Reporter,
                                                            commonService: CommonService[F],
                                                            sessionsService: SessionsService[F, _, _]) {

  import MessagingService._

  /**
    * Poll message from session's ongoing queue.
    */
  def longPollingSubscribe(qsid: Qsid, rh: RequestHeader): F[Response.Http[F]] = {
    for {
      _ <- sessionsService.createAppIfNeeded(qsid, rh, createTopic(qsid))
      maybeApp <- sessionsService.getApp(qsid)
      // See webSocketMessaging()
      maybeMessage <- maybeApp.fold(SomeReloadMessageF)(_.frontend.outgoingMessages.pull())
    } yield {
      maybeMessage match {
        case None => commonGoneResponse
        case Some(message) =>
          Response.Http(
            status = Response.Status.Ok,
            message = message,
            headers = commonResponseHeaders
          )
      }
    }
  }

  /**
    * Push message to session's incoming queue.
    */
  def longPollingPublish(qsid: Qsid, data: LazyBytes[F]): F[Response.Http[F]] = {
    for {
      topic <- takeTopic(qsid)
      message <- data.toStrictUtf8
      _ <- topic.offer(message)
    } yield commonOkResponse
  }

  def webSocketMessaging(qsid: Qsid, rh: RequestHeader, incomingMessages: Stream[F, String]): F[Response.WebSocket[F]] = {
    sessionsService.createAppIfNeeded(qsid, rh, incomingMessages) flatMap { _ =>
      sessionsService.getApp(qsid) map {
        case Some(app) => Response(Status.Ok, app.frontend.outgoingMessages, Nil)
        case None =>
          // Respond with reload message because app was not found.
          // In this case it means that server had ben restarted and
          // do not have an information about the state which had been
          // applied to render of the page on a client side.
          Response(Status.Ok, Stream.eval(Frontend.ReloadMessage), Nil)
      }
    }
  }

  /**
    * Sessions created via long polling subscription
    * takes messages from topics stored in this table.
    */
  private val longPollingTopics = mutable.Map.empty[Qsid, Queue[F, String]]

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
    body = LazyBytes.empty[F],
    headers = commonResponseHeaders
  )

  /**
    * Same response for all 'subscribe' requests
    * where outgoing stream is consumed.
    */
  private val commonGoneResponse = Response(
    status = Response.Status.Gone,
    body = LazyBytes.empty[F],
    headers = commonResponseHeaders
  )

  private def takeTopic(qsid: Qsid) =
    Effect[F].delay {
      if (longPollingTopics.contains(qsid)) longPollingTopics(qsid)
      else throw new Exception(s"There is no long-polling topic matching $qsid")
    }

  private def createTopic(qsid: Qsid) =
    longPollingTopics.synchronized {
      val topic = Queue[F, String]()
      longPollingTopics.put(qsid, topic)
      topic.stream
    }
}

private[korolev] object MessagingService {

  def SomeReloadMessageF[F[_]: Effect]: F[Option[String]] =
    Effect[F].pure(Option(ReloadMessage))
}