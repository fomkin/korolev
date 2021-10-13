package korolev.effect.io

import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousChannelGroup, AsynchronousCloseException, AsynchronousServerSocketChannel, AsynchronousSocketChannel, CompletionHandler}
import korolev.data.BytesLike
import korolev.effect.{Close, Effect, Queue, Stream}
import korolev.effect.syntax._

import scala.concurrent.ExecutionContext

/**
  * Stream API for AsynchronousServerSocketChannel.
  * Use `ServerSocket.bind` to start listening.
  * @see [[AsynchronousServerSocketChannel]]
  */
class ServerSocket[F[_]: Effect, B: BytesLike](channel: AsynchronousServerSocketChannel,
                                               bufferSize: Int) extends Stream[F, RawDataSocket[F, B]] {

  @volatile private var canceled = false

  def pull(): F[Option[RawDataSocket[F, B]]] = Effect[F].promise { cb =>
    if (canceled) cb(Right(None)) else {
      channel.accept((), new CompletionHandler[AsynchronousSocketChannel, Unit] {
        def completed(socket: AsynchronousSocketChannel, notUsed: Unit): Unit =
          cb(Right(Some(new RawDataSocket[F, B](socket, ByteBuffer.allocate(bufferSize), ByteBuffer.allocate(bufferSize)))))
        def failed(throwable: Throwable, notUsed: Unit): Unit = throwable match {
          case _: AsynchronousCloseException if canceled =>
            // Its okay. Accepting new connection was
            // stopped by Stream cancel
            cb(Right(None))
          case _ => cb(Left(throwable))
        }
      })
    }
  }

  def cancel(): F[Unit] = Effect[F].delay {
    canceled = true
    channel.close()
  }
}

object ServerSocket {

  /**
    * Bind server socket to `address` and accept connections with `f`.
    *
    * @see [[bind]]
    */
  def accept[F[_]: Effect, B: BytesLike](address: SocketAddress,
                                         backlog: Int = 0,
                                         bufferSize: Int = 8096,
                                         group: AsynchronousChannelGroup = null,
                                         gracefulShutdown: Boolean = false)
                                        (f: RawDataSocket[F, B] => F[Unit])
                                        (implicit ec: ExecutionContext): F[ServerSocketHandler[F]] =
    bind(address, backlog, bufferSize, group = group).flatMap { server =>
      val connectionsQueue = Queue[F, F[Unit]]()
      server
        .foreach { connection =>
          f(connection)
            .start
            .flatMap(f => connectionsQueue.enqueue(f.join()))
        }
        .start
        .map { serverFiber =>
          new ServerSocketHandler[F] {

            def awaitShutdown(): F[Unit] = {
              if (gracefulShutdown) {
                serverFiber.join() *>
                  connectionsQueue.stop() *>
                  connectionsQueue.stream.foreach(identity)
              } else {
                serverFiber.join()
              }
            }

            def stopServingRequests(): F[Unit] =
              server.cancel()
          }
        }
    }

  /**
    * Open an AsynchronousServerSocketChannel and bind it to `socketAddress`.
    * @see [[AsynchronousServerSocketChannel]]
    */
  def bind[F[_]: Effect, B: BytesLike](socketAddress: SocketAddress,
                                       backlog: Int = 0,
                                       bufferSize: Int = 8096,
                                       group: AsynchronousChannelGroup = null): F[ServerSocket[F, B]] =
    Effect[F].delay {
      val channel = AsynchronousServerSocketChannel
        .open(group)
        .bind(socketAddress, backlog)
      new ServerSocket[F, B](channel, bufferSize)
    }

  sealed trait ServerSocketHandler[F[_]] {

    /**
      * Awaits server socket close.
     * If server configured with graceful shutdown, waits until all client connections are closed.
      */
    def awaitShutdown(): F[Unit]

    /**
      * Stop accepting new connections and serving requests.
      *
      * If you are using server with HTTP note that WebSockets
      * and other request without content length will be open
      * until connection closed by a client.
      */
    def stopServingRequests(): F[Unit]
  }

  object ServerSocketHandler {
    implicit def serverSocketHandlerCloseInstance[F[_]: Effect]: Close[F, ServerSocketHandler[F]] =
      new Close[F, ServerSocketHandler[F]] {
        def onClose(that: ServerSocketHandler[F]): F[Unit] =
          that.awaitShutdown()

        def close(that: ServerSocketHandler[F]): F[Unit] =
          that.stopServingRequests()
      }
  }
}