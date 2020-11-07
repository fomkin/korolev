package korolev.effect

import java.nio.channels.CompletionHandler

package object io {

  private[io] def completionHandler[T](cb: Either[Throwable, T] => Unit): CompletionHandler[T, Unit] =
    new CompletionHandler[T, Unit] {
      def completed(v: T, a: Unit): Unit = cb(Right(v))
      def failed(error: Throwable, a: Unit): Unit = cb(Left(error))
    }
}
