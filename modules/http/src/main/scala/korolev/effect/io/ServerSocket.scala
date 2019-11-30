package korolev.effect.io

import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.{AsynchronousChannelGroup, AsynchronousCloseException, AsynchronousServerSocketChannel, AsynchronousSocketChannel, CompletionHandler}

import korolev.effect.{Effect, Queue, Stream}
import korolev.effect.syntax._

import scala.concurrent.ExecutionContext

/**
  * Stream API for AsynchronousServerSocketChannel.
  * Use `ServerSocket.bind` to start listening.
  * @see [[AsynchronousServerSocketChannel]]
  */
class ServerSocket[F[_]: Effect](channel: AsynchronousServerSocketChannel,
                                 readBufferSize: Int) extends Stream[F, RawDataSocket[F]] {

  @volatile private var canceled = false

  def pull(): F[Option[RawDataSocket[F]]] = Effect[F].promise { cb =>
    if (canceled) cb(Right(None)) else {
      channel.accept((), new CompletionHandler[AsynchronousSocketChannel, Unit] {
        def completed(socket: AsynchronousSocketChannel, notUsed: Unit): Unit =
          cb(Right(Some(new RawDataSocket[F](socket, ByteBuffer.allocate(readBufferSize)))))
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
  def accept[F[_]: Effect](address: SocketAddress,
                           backlog: Int = 0,
                           readBufferSize: Int = 8096,
                           group: AsynchronousChannelGroup = null)
                          (f: RawDataSocket[F] => F[Unit])
                          (implicit ec: ExecutionContext): F[ServerSocketHandler[F]] =
    bind(address, backlog, readBufferSize, group = group).flatMap { server =>
      val connectionsQueue = Queue[F, F[Unit]]()
      server
        .foreach { connection =>
          f(connection)
            .start
            .flatMap(f => connectionsQueue.offer(f.join()))
        }
        .start
        .map { fiber =>
          new ServerSocketHandler[F] {
            def awaitShutdown(): F[_] =
              fiber.join() *>
                connectionsQueue.stop() *>
                connectionsQueue.stream.foreach(identity)
            def stopServingRequests(): F[_] =
              server.cancel()
          }
        }
    }

  /**
    * Open an AsynchronousServerSocketChannel and bind it to `socketAddress`.
    * @see [[AsynchronousServerSocketChannel]]
    */
  def bind[F[_]: Effect](socketAddress: SocketAddress,
                         backlog: Int = 0,
                         readBufferSize: Int = 8096,
                         group: AsynchronousChannelGroup = null): F[ServerSocket[F]] =
    Effect[F].delay {
      val channel = AsynchronousServerSocketChannel
        .open(group)
        .bind(socketAddress, backlog)
      new ServerSocket[F](channel, readBufferSize)
    }

  sealed trait ServerSocketHandler[F[_]] {

    /**
      * Await until all connections are closed.
      */
    def awaitShutdown(): F[_]

    /**
      * Stop accepting new connections and serving requests.
      *
      * If you are using server with HTTP note that WebSockets
      * and other request without content length will be open
      * until connection closed by a client.
      */
    def stopServingRequests(): F[_]
  }
}