package korolev.effect.io

import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousChannelGroup, AsynchronousSocketChannel, CompletionHandler}

import korolev.data.ByteVector
import korolev.effect.{Effect, Stream}

sealed class RawDataSocket[F[_]: Effect](channel: AsynchronousSocketChannel,
                                         buffer: ByteBuffer) extends DataSocket[F] {

  private var inProgress = false
  private var canceled = false

  val stream: Stream[F, ByteVector] = new Stream[F, ByteVector] {
    def pull(): F[Option[ByteVector]] = Effect[F].promise { cb =>
      if (canceled) {
        cb(Right(None))
      } else {
        buffer.clear()
        if (inProgress) {
          println(s"${Console.RED}Concurrent pull() detected in RawDataSocket(${channel.getRemoteAddress}).stream. ${Console.RESET}")
          Thread
            .currentThread()
            .getStackTrace
//            .filter(_.getClassName.contains("korolev"))
            .foreach { ste =>
              println(s"${Console.RED}  $ste${Console.RESET}")
            }
        }
        inProgress = true
        channel.read(buffer, (), new CompletionHandler[Integer, Unit] {
          def completed(bytesRead: Integer, notUsed: Unit): Unit = {
            inProgress = false
            if (bytesRead < 0) {
              // Socket was closed
              cb(Right(None))
            } else {
              val array = buffer.array().slice(0, bytesRead)
              cb(Right(Some(ByteVector(array))))
            }
          }

          def failed(throwable: Throwable, notUsed: Unit): Unit = {
            cb(Left(throwable))
          }
        })
      }
    }

    def cancel(): F[Unit] =
      Effect[F].delay {
        if (!canceled) {
          canceled = true
          channel.close()
        }
      }
  }

  def write(bytes: ByteVector): F[Unit] = {
    val buffer = ByteBuffer.wrap(bytes.mkArray) // TODO Maybe it should be static allocated buffer
    Effect[F].promise { cb =>
      val handler = new CompletionHandler[Integer, Unit] {
        def completed(bytesWritten: Integer, notUsed: Unit): Unit =
          if (buffer.hasRemaining) channel.write(buffer, (), this)
          else cb(Right(()))
        def failed(throwable: Throwable, notUsed: Unit): Unit =
          cb(Left(throwable))
      }
      channel.write(buffer, (), handler)
    }
  }
}

object RawDataSocket {

  def connect[F[_]: Effect](address: SocketAddress,
                            buffer: ByteBuffer = ByteBuffer.allocate(8096),
                            group: AsynchronousChannelGroup = null): F[RawDataSocket[F]] =
    Effect[F].promise { cb =>
      val channel = AsynchronousSocketChannel.open(group)
      lazy val ds = new RawDataSocket[F](channel, buffer)
      channel.connect(address, (), completionHandler[Void](cb.compose(_.map(_ => ds))))
    }
}
