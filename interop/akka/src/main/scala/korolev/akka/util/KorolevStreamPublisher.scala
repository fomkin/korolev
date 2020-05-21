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

package korolev.akka.util

import korolev.akka.util.KorolevStreamPublisher.MultipleSubscribersProhibitedException
import korolev.effect.{Effect, Hub, Reporter, Stream}
import korolev.effect.syntax._
import org.reactivestreams.{Publisher, Subscriber, Subscription}

final class KorolevStreamPublisher[F[_] : Effect, T](stream: Stream[F, T],
                                                     fanout: Boolean) extends Publisher[T] {

  private implicit val reporter: Reporter = Reporter.PrintReporter

  private var hasActualSubscriber = false

  private val hub =
    if (fanout) Hub(stream)
    else null

  private final class Counter {

    @volatile var n: Long = 0
    @volatile var pending: Effect.Promise[Unit] = _
    val res = Right(())

    def value(): F[Long] = Effect[F].delay(n)

    def decOrLock(): F[Unit] = Effect[F].promise[Unit] { cb =>
      this.synchronized { // FIXME use CAS?
        if (n == 0) {
          pending = cb
        } else {
          n -= 1
          cb(res)
        }
      }
    }

    def unsafeSet(x: Long): Unit =
      this.synchronized {
        n = x
        if (x > 0 && pending != null) {
          val cb = pending
          pending = null
          cb(res)
        }
      }
  }

  private final class StreamSubscription(stream: Stream[F, T],
                                         subscriber: Subscriber[_ >: T]) extends Subscription {

    private val counter = new Counter()

    private def loop(): F[Unit] =
      for {
        _ <- counter.decOrLock()
        maybeItem <- stream.pull()
        _ <- maybeItem match {
          case Some(item) =>
            subscriber.onNext(item)
            loop()
          case None =>
            subscriber.onComplete()
            Effect[F].unit
        }
      } yield ()

    loop().runAsync {
      case Left(error) => subscriber.onError(error)
      case Right(_) => ()
    }

    def request(n: Long): Unit = {
      counter.unsafeSet(n)
    }

    def cancel(): Unit = {
      stream
        .cancel()
        .runAsyncForget
    }
  }

  def subscribe(subscriber: Subscriber[_ >: T]): Unit = {
    if (hub != null) {
      hub.newStream().runAsyncSuccess { newStream =>
        val subscription = new StreamSubscription(newStream, subscriber)
        subscriber.onSubscribe(subscription)
      }
    } else {
      if (hasActualSubscriber)
        throw MultipleSubscribersProhibitedException()
      subscriber.onSubscribe(new StreamSubscription(stream, subscriber))
    }
    hasActualSubscriber = true
  }
}

object KorolevStreamPublisher {
  final case class MultipleSubscribersProhibitedException()
    extends Exception("Multiple subscribers prohibited for this KorolevStreamPublisher")
}