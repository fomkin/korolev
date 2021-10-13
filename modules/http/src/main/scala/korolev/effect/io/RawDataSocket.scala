package korolev.effect.io

import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousChannelGroup, AsynchronousSocketChannel, CompletionHandler}
import korolev.data.BytesLike
import korolev.data.syntax._
import korolev.effect.io.DataSocket.CloseReason
import korolev.effect.{Effect, Stream}
import korolev.effect.syntax._

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

sealed class RawDataSocket[F[_]: Effect, B: BytesLike](channel: AsynchronousSocketChannel,
                                                       readBuffer: ByteBuffer,
                                                       writeBuffer: ByteBuffer) extends DataSocket[F, B] {

  val stream: Stream[F, B] = new Stream[F, B] {

    def pull(): F[Option[B]] = read(readBuffer).map {
      case -1 => None
      case _ =>
        readBuffer.flip()
        val bytes = BytesLike[B].copyBuffer(readBuffer)
        readBuffer.clear()
        Some(bytes)
    }

    def cancel(): F[Unit] = {
      @tailrec def loop(): Unit = {
        val ref = state.get()
        if (ref.closed.isEmpty) {
          val reason = CloseReason.StreamCanceled
          if (state.compareAndSet(ref, ref.copy(closed = Some(reason)))) {
            dispatchClose(reason)
          } else {
            loop()
          }
        }
      }
      Effect[F].delay(loop())
    }
  }

  def read(buffer: ByteBuffer): F[Int] = Effect[F].promise[Int] { cb =>
    @tailrec def unsetInProgress(maybeReason: Option[CloseReason]): Unit = {
      val ref = state.get
      if (ref.closed.isEmpty) {
        val newState = ref.copy(inProgress = false, closed = maybeReason)
        if (state.compareAndSet(ref, newState)) {
          maybeReason match {
            case Some(reason) => dispatchClose(reason)
            case None => // do nothing
          }
        } else {
          unsetInProgress(maybeReason)
        }
      } else {
        val newState = ref.copy(inProgress = false)
        if (!state.compareAndSet(ref, newState)) {
          unsetInProgress(maybeReason)
        }
      }
    }
    @tailrec def loop(): Unit = {
      val ref = state.get
      if (ref.closed.isEmpty) {
        if (ref.inProgress) {
          cb(Left(new IllegalStateException("This socket already in read state")))
        } else  {
          // Get reading lock
          if (state.compareAndSet(ref, ref.copy(inProgress = true))) {
            val handler = new CompletionHandler[Integer, Unit] {
              def completed(result: Integer, attachment: Unit): Unit = {
                if (result == -1) {
                  // EOF
                  unsetInProgress(Some(CloseReason.ByPeer))
                } else {
                  unsetInProgress(None)
                }
                cb(Right(result))
              }
              def failed(exc: Throwable, attachment: Unit): Unit = {
                val reason = Some(CloseReason.Error(exc))
                unsetInProgress(reason)
                cb(Left(exc))
              }
            }
            channel.read(buffer, (), handler)
          } else {
            loop()
          }
        }
      } else {
        cb(Right(-1))
      }
    }
    loop()
  }

  def write(buffer: ByteBuffer): F[Unit] = {
    Effect[F].promise { cb =>
      val ref = state.get
      if (ref.closed.isEmpty) {
        val handler = new CompletionHandler[Integer, Unit] {
          def completed(bytesWritten: Integer, notUsed: Unit): Unit =
            if (buffer.hasRemaining) channel.write(buffer, (), this)
            else cb(Right(()))
          def failed(throwable: Throwable, notUsed: Unit): Unit =
            cb(Left(throwable))
        }
        channel.write(buffer, (), handler)
      } else {
        cb(Left(new IllegalStateException(s"Try to write in closed (${ref.closed}) socket")))
      }
    }
  }

  def write(bytes: B): F[Unit] = {
//    writeBuffer.clear()
//    BytesLike[B].copyToBuffer(bytes, writeBuffer)
//    writeBuffer.flip()
//    write(writeBuffer)
    val buffer = bytes.asBuffer // TODO Maybe it should be static allocated buffer
    write(buffer)
  }

  def onClose(): F[CloseReason] = Effect[F].promise[CloseReason] { cb =>
    @tailrec def loop(): Unit = {
      val ref = state.get
      ref.closed match {
        case Some(reason) => cb(Right(reason))
        case None =>
          if (!state.compareAndSet(ref, ref.copy(onCloseCbs = cb :: ref.onCloseCbs))) {
            loop()
          }
      }
    }
    loop()
  }

  private def dispatchClose(reason: CloseReason): Unit = {
    val ref = state.get
    ref.onCloseCbs.foreach(cb => cb(Right(reason)))
  }

  private type OnCloseCallback = Effect.Promise[CloseReason]
  private case class State(inProgress: Boolean = false,
                           closed: Option[CloseReason] = None,
                           onCloseCbs: List[OnCloseCallback] = Nil)

  private val state = new AtomicReference(State())
}

object RawDataSocket {

  def connect[F[_]: Effect, B: BytesLike](address: SocketAddress,
                                          readBuffer: ByteBuffer = ByteBuffer.allocate(8096),
                                          writeBuffer: ByteBuffer = ByteBuffer.allocate(8096),
                                          group: AsynchronousChannelGroup = null): F[RawDataSocket[F, B]] =
    Effect[F].promise { cb =>
      val channel = AsynchronousSocketChannel.open(group)
      lazy val ds = new RawDataSocket[F, B](channel, readBuffer, writeBuffer)
      channel.connect(address, (), completionHandler[Void](cb.compose(_.map(_ => ds))))
    }
}
