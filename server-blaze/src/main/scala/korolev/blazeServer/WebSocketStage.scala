package korolev.blazeServer

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
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
