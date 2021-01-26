package korolev.zio.streams

import korolev.effect.{Queue, Effect => KorolevEffect, Stream => KorolevStream}
import zio.{Runtime, Task}
import korolev.zio._
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import zio.stream.{ZSink, ZStream}

class ZIOStreamsInteropTest  extends AsyncFlatSpec with Matchers {

  implicit val runtime = Runtime.default
  implicit val effect = taskEffectInstance[Any](runtime)

  "KorolevStream.toZStream" should "provide zio.Stream that contain exactly same values as original Korolev stream" in {

    val values = List(1, 2, 3, 4, 5)

    val io = KorolevStream(values: _*)
      .mat[Task]()
      .flatMap { korolevStream: KorolevStream[Task, Int] =>
        korolevStream
          .toZStream
          .run(ZSink.foldLeft(List.empty[Int]){ case (acc, v) => acc :+ v})
      }

     runtime.unsafeRunToFuture(io).map { result =>
        result shouldEqual values
      }
  }

  it should "provide zio.Stream which handle values asynchronously" in {
    val queue = Queue[Task, Int]()
    val stream: KorolevStream[Task, Int] = queue.stream
    val io =
      for {
        fiber <- KorolevEffect[Task]
          .start {
            Task(stream
              .toZStream.
              run(ZSink.foldLeft(List.empty[Int]){ case (acc, v) => acc :+ v})
            )
          }
        _ <- queue.offer(1)
        _ <- queue.offer(2)
        _ <- queue.offer(3)
        _ <- queue.offer(4)
        _ <- queue.offer(5)
        _ <- queue.stop()
        result <- fiber.join()
      } yield {
        result.map(r => r shouldEqual List(1, 2, 3, 4, 5))
      }
    runtime.unsafeRunToFuture(io.flatten)
  }

  "ZStream.toKorolevStream" should "provide korolev.effect.Stream that contain exactly same values as original fs2.Stream" in {
    val values = Vector(1, 2, 3, 4, 5)
    val io = ZStream.fromIterable(values)
      .toKorolev()
      .flatMap { korolevStream =>
        korolevStream
          .fold(Vector.empty[Int])((acc, value) => acc :+ value)
          .map(result => result shouldEqual values)
      }
    runtime.unsafeRunToFuture(io)
  }


}
