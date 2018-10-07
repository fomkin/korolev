/*
 * Copyright 2017-2018 Aleksey Fomkin
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

package korolev.blazeServer

import org.http4s.blaze.pipeline._
import org.http4s.blaze.util.Execution.trampoline
import org.http4s.blaze.pipeline.stages.SerializingStage
import org.http4s.websocket.WebsocketBits.WebSocketFrame

import scala.util.Failure
import scala.util.Success

trait WebSocketStage extends TailStage[WebSocketFrame] {

  def name: String = "WebSocket Stage"

  def onMessage(msg: WebSocketFrame): Unit
  def onDirtyDisconnect(e: Throwable): Unit

  /////////////////////////////////////////////////////////////

  private def _wsLoop(): Unit = {
    channelRead().onComplete {
      case Success(msg) =>
        logger.debug(s"Received Websocket message: $msg")
        try {
          onMessage(msg)
          _wsLoop()
        }
        catch {case t: Throwable =>
          logger.error(t)("WSStage onMessage threw exception. Shutting down.")
          onDirtyDisconnect(t)
          sendOutboundCommand(Command.Disconnect)
        }

      case Failure(t) =>
        logger.debug(t)("error on Websocket read loop")
        onDirtyDisconnect(t)
        sendOutboundCommand(Command.Disconnect)
    }(trampoline)
  }

  override protected def stageStartup(): Unit = {
    super.stageStartup()
    _wsLoop()
  }
}

object WebSocketStage {
  def bufferingSegment(stage: WebSocketStage): LeafBuilder[WebSocketFrame] = {
    TrunkBuilder(new SerializingStage[WebSocketFrame]).cap(stage)
  }
}
