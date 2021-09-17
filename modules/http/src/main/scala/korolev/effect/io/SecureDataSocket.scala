package korolev.effect.io

import jdk.jshell.spi.ExecutionControl.NotImplementedException
import korolev.data.BytesLike
import korolev.effect
import korolev.effect.Effect
import korolev.effect.syntax._

import java.nio.ByteBuffer
import java.util.concurrent.Executor
import javax.net.ssl.SSLEngineResult.{HandshakeStatus, Status}
import javax.net.ssl.{SSLEngine, SSLHandshakeException}
import scala.collection.mutable

class SecureDataSocket[F[_] : Effect, B: BytesLike] private(engine: SSLEngine,
                                                            socket: RawDataSocket[F, B]) extends DataSocket[F, B] {

  import SecureDataSocket.UnwrapStatus

  val stream: effect.Stream[F, B] = new effect.Stream[F, B] {

    def pull(): F[Option[B]] = Effect[F].delayAsync {
      if (!canceled) {
        doUnwrap() map {
          case UnwrapStatus.Ok =>
            peerAppBuffer.flip()
            val bytes = BytesLike[B].copyBuffer(peerAppBuffer)
            peerAppBuffer.compact()
            Some(bytes)
          case _ =>
            closeInbound()
            None
        }
      } else {
        Effect[F].pure(None)
      }
    }

    def cancel(): F[Unit] = Effect[F].delayAsync {
      if (!canceled) {
        canceled = true
        engine.closeOutbound()
        socket.stream.cancel()
      } else {
        Effect[F].unit
      }
    }
  }

  def write(bytes: B): F[Unit] = Effect[F].delayAsync {
    appBuffer.clear()
    BytesLike[B].copyToBuffer(bytes, appBuffer)
    appBuffer.flip()
    doWrap().unit
  }

  def onClose(): F[DataSocket.CloseReason] =
    socket.onClose()

  private val appBuffer: ByteBuffer = ByteBuffer.allocate(engine.getSession.getApplicationBufferSize)
  @volatile private var netBuffer: ByteBuffer = ByteBuffer.allocate(engine.getSession.getPacketBufferSize)
  @volatile private var peerAppBuffer: ByteBuffer = ByteBuffer.allocate(engine.getSession.getApplicationBufferSize)
  @volatile private var peerNetBuffer: ByteBuffer = ByteBuffer.allocate(engine.getSession.getPacketBufferSize)
  @volatile private var canceled = false

  private def enlargeBuffer(buffer: ByteBuffer, newRemaining: Int) = {
    if (buffer.capacity() < newRemaining) {
      val newBuffer = ByteBuffer.allocate(buffer.remaining() + newRemaining)
      buffer.flip()
      newBuffer.put(buffer)
      newBuffer
    } else {
      buffer
    }
  }

  private def handshake(blockingExecutor: Executor): F[Unit] = {

    def handshakeLoop(handshakeStatus: HandshakeStatus): F[Unit] = handshakeStatus match {
      case HandshakeStatus.NOT_HANDSHAKING | HandshakeStatus.FINISHED =>
        peerAppBuffer.clear()
        Effect[F].unit
      case HandshakeStatus.NEED_WRAP =>
        doWrap() flatMap {
          case true => handshakeLoop(engine.getHandshakeStatus)
          case false => throw new SSLHandshakeException("Invalid wrap status. CLOSED given but OK expected")
        }
      case HandshakeStatus.NEED_UNWRAP =>
        doUnwrap().flatMap {
          case UnwrapStatus.Ok => handshakeLoop(engine.getHandshakeStatus)
          case UnwrapStatus.ClosedByPeer => throw new SSLHandshakeException("Peer has closed connection during handshake")
          case UnwrapStatus.ClosedByEngine => throw new SSLHandshakeException("Invalid unwrap status. CLOSED given but OK expected")
        }
      case HandshakeStatus.NEED_TASK =>
        val effects = getDelegatedTasks.map { task =>
          Effect[F].promise[Unit] { cb =>
            blockingExecutor.execute {
              () => {
                task.run()
                cb(Right(()))
              }
            }
          }
        }
        Effect[F]
          .sequence(effects)
          .flatMap(_ => handshakeLoop(engine.getHandshakeStatus))
      case HandshakeStatus.NEED_UNWRAP_AGAIN =>
        throw new NotImplementedException("DTLS handshake doesn't supported yet")
    }

    handshakeLoop(engine.getHandshakeStatus)
  }

  private def doWrap(): F[Boolean] = Effect[F].delayAsync {
    netBuffer.clear()
    engine.wrap(appBuffer, netBuffer).getStatus match {
      case Status.OK =>
        netBuffer.flip()
        socket.write(netBuffer).as(true)
      case Status.BUFFER_OVERFLOW =>
        // netBuffer doesn't have required capacity
        netBuffer = ByteBuffer.allocate(engine.getSession.getPacketBufferSize)
        doWrap()
      case Status.CLOSED => Effect[F].pure(false)
    }
  }

  private def doUnwrap(): F[UnwrapStatus] =
    for {
      hasData <- {
        peerNetBuffer.position() match {
          case 0 => socket.read(peerNetBuffer).map(x => if (x < 0) false else true)
          case _ => Effect[F].pure(true)
        }
      }
      result <- if (!hasData) Effect[F].pure(UnwrapStatus.ClosedByPeer) else {
        peerNetBuffer.flip()
        val result = engine.unwrap(peerNetBuffer, peerAppBuffer)
        peerNetBuffer.compact()
        result.getStatus match {
          case Status.OK => Effect[F].pure(UnwrapStatus.Ok)
          case Status.CLOSED => Effect[F].pure(UnwrapStatus.ClosedByEngine)
          case Status.BUFFER_UNDERFLOW =>
            peerNetBuffer = enlargeBuffer(peerNetBuffer, engine.getSession.getPacketBufferSize)
            socket
              .read(peerNetBuffer)
              .flatMap {
                case -1 => Effect[F].pure(UnwrapStatus.ClosedByPeer: UnwrapStatus)
                case _ => doUnwrap()
              }
          case Status.BUFFER_OVERFLOW =>
            peerAppBuffer = enlargeBuffer(peerAppBuffer, engine.getSession.getApplicationBufferSize)
            doUnwrap()
        }
      }
    } yield result

  private def getDelegatedTasks: List[Runnable] = {
    val tasks = mutable.Buffer.empty[Runnable]
    var task: Runnable = null
    while ( {
      task = engine.getDelegatedTask;
      task != null
    }) {
      tasks += task
    }

    tasks.toList
  }

  private def closeInbound(): Unit = {
    try {
      engine.closeInbound()
    } catch {
      case _: Throwable =>
        // Do nothing.
        // This engine.closeInbound() always throws an exception as I see.
        // Every implementation invokes engine.closeInbound() on socket close.
    }
  }
}

object SecureDataSocket {

  def forClientMode[F[_] : Effect, B: BytesLike](socket: RawDataSocket[F, B],
                                                 engine: SSLEngine,
                                                 blockingExecutor: Executor): F[SecureDataSocket[F, B]] = {
    engine.setUseClientMode(true)
    apply(socket, engine, blockingExecutor)
  }

  def apply[F[_] : Effect, B: BytesLike](socket: RawDataSocket[F, B],
                                         engine: SSLEngine,
                                         blockingExecutor: Executor): F[SecureDataSocket[F, B]] = {
    engine.beginHandshake()
    val secureSocket = new SecureDataSocket[F, B](engine, socket)
    secureSocket.handshake(blockingExecutor).as(secureSocket)
  }

  private sealed trait UnwrapStatus

  private object UnwrapStatus {

    case object Ok extends UnwrapStatus

    case object ClosedByEngine extends UnwrapStatus

    case object ClosedByPeer extends UnwrapStatus
  }
}
