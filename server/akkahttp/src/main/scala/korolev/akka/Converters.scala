package korolev.akka

import akka.NotUsed
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Sink, Source}
import korolev.akka.util.{KorolevStreamPublisher, KorolevStreamSubscriber}
import korolev.effect.{Effect, Stream}
import org.reactivestreams.Publisher

object Converters {

  implicit final class SinkCompanionOps(value: Sink.type) {
    def korolevStream[F[_]: Effect, T]: Sink[T, Stream[F, T]] = {
      val subscriber = new KorolevStreamSubscriber[F, T]()
      Sink
        .fromSubscriber(subscriber)
        .mapMaterializedValue(_ => subscriber)
    }
  }

  implicit final class StreamCompanionOps(value: Stream.type) {
    def fromPublisher[F[_]: Effect, T](publisher: Publisher[T]): Stream[F, T] = {
      val result = new KorolevStreamSubscriber[F, T]()
      publisher.subscribe(result)
      result
    }
  }

  implicit final class KorolevStreamsOps[F[_]: Effect, T](stream: Stream[F, T]) {

    /**
      * Converts korolev [[korolev.effect.Stream]] to [[Publisher]].
      *
      * If `fanout` is `true`, the `Publisher` will support multiple `Subscriber`s and
      * the size of the `inputBuffer` configured for this operator becomes the maximum number of elements that
      * the fastest [[org.reactivestreams.Subscriber]] can be ahead of the slowest one before slowing
      * the processing down due to back pressure.
      *
      * If `fanout` is `false` then the `Publisher` will only support a single `Subscriber` and
      * reject any additional `Subscriber`s with [[korolev.akka.util.KorolevStreamPublisher.MultipleSubscribersProhibitedException]].
      */
    def asPublisher(fanout: Boolean = false): Publisher[T] =
      new KorolevStreamPublisher(stream, fanout)

    def asAkkaSource: Source[T, NotUsed] = {
      val publisher = new KorolevStreamPublisher(stream, fanout = false)
      Source.fromPublisher(publisher)
    }
  }
}
