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

import korolev.effect.{Effect, Stream}
import org.reactivestreams.{Subscriber, Subscription}

final class KorolevStreamSubscriber[F[_]: Effect,T] extends Stream[F, T] with Subscriber[T] {

  private var subscription: Subscription = _

  private var pullCallback: Either[Throwable, Option[T]] => Unit = _

  private var consumedCallback: Either[Throwable, Unit] => Unit = _

  private var completeValue: Either[Throwable, Unit] = _

  def onSubscribe(subscription: Subscription): Unit = {
    this.subscription = subscription
    if (pullCallback != null)
      subscription.request(1)
  }

  def onNext(value: T): Unit = {
    val cb = pullCallback
    pullCallback = null
    cb(Right(Some(value)))
  }

  def onError(error: Throwable): Unit = {
    completeWith(Left(error))
    val cb = pullCallback
    pullCallback = null
    cb(Left(error))
  }

  def onComplete(): Unit = {
    completeWith(Right(()))
    val cb = pullCallback
    pullCallback = null
    cb(Right(None))
  }

  private def completeWith(that: Either[Throwable, Unit]): Unit = {
    completeValue = that
    if (consumedCallback != null) {
      val cb = consumedCallback
      consumedCallback = null
      cb(that)
    }
  }

  def pull(): F[Option[T]] = Effect[F].promise { cb =>
    if (completeValue == null) {
      pullCallback = cb
      if (subscription != null) {
        subscription.request(1)
      }
    } else {
      cb(completeValue.map(_ => None))
    }
  }

  def cancel(): F[Unit] = Effect[F].delay(subscription.cancel())

  val consumed: F[Unit] = Effect[F].promise { cb =>
    if (completeValue != null) cb(completeValue)
    else consumedCallback = cb
  }

  val size: Option[Long] = None
}
