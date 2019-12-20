package korolev.akka

import akka.NotUsed
import akka.stream.scaladsl.{Sink, Source}
import korolev.effect.{Effect, Stream}
import org.reactivestreams.{Subscriber, Subscription}

object Converters {

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

  implicit final class SinkCompanionOps(value: Sink.type) {
    def korolevStream[F[_]: Effect, T]: Sink[T, Stream[F, T]] = {
      val subscriber = new KorolevStreamSubscriber[F, T]()
      Sink
        .fromSubscriber(subscriber)
        .mapMaterializedValue(_ => subscriber)
    }
  }

  implicit final class KorolevStreamsOps[F[_]: Effect, T](value: Stream[F, T]) {
    def asSource: Source[T, NotUsed] = Source
      .unfoldAsync[NotUsed, T](NotUsed) { _ =>
        Effect[F].toFuture(
          Effect[F].map(value.pull()) { vOpt =>
            vOpt.map(v => (NotUsed, v))
          }
        )
      }
  }
}
