package korolev.zio.streams

import korolev.effect.{Queue, Effect => KorolevEffect, Stream => KorolevStream}
import zio.{Runtime, Task}
import korolev.zio._
import _root_.zio._
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import zio.stream.{ZSink, ZStream}

class ZIOStreamsInteropTest extends AsyncFlatSpec with Matchers {

  private val runtime = Runtime.default
  private implicit val effect: KorolevEffect[Task] = taskEffectInstance[Any](runtime)

  "KorolevStream.toZStream" should "provide zio.Stream that contain exactly same values as original Korolev stream" in {

    val values = List(1, 2, 3, 4, 5)

    val io = KorolevStream(values: _*)
      .mat[Task]()
      .flatMap { (korolevStream: KorolevStream[Task, Int]) =>
        korolevStream.toZStream
          .run(ZSink.foldLeft(List.empty[Int]) { case (acc, v) => acc :+ v })
      }

    zio.Unsafe.unsafeCompat { implicit u =>
      runtime.unsafe.runToFuture(io).map { result =>
        result shouldEqual values
      }
    }
  }

  it should "provide zio.Stream which handle values asynchronously" in {
    val queue = Queue[Task, Int]()
    val stream: KorolevStream[Task, Int] = queue.stream
    val io =
      for {
        fiber <- stream.toZStream.run(ZSink.foldLeft(List.empty[Int]) { case (acc, v) => acc :+ v }).fork
        _ <- queue.offer(1)
        _ <- queue.offer(2)
        _ <- queue.offer(3)
        _ <- queue.offer(4)
        _ <- queue.offer(5)
        _ <- queue.stop()
        result <- fiber.join
      } yield {
        result shouldEqual List(1, 2, 3, 4, 5)
      }
    zio.Unsafe.unsafeCompat { implicit u =>
      runtime.unsafe.runToFuture(io)
    }
  }

  "ZStream.toKorolevStream" should "provide korolev.effect.Stream that contain exactly same values as original zio.Stream" in {

    val v1 = Vector(1, 2, 3, 4, 5)
    val v2 = Vector(5, 4, 3, 2, 1)
    val values = v1 ++ v2
    val io =
      ZStream
        .fromIterable(v1)
        .concat(ZStream.fromIterable(v2)) // concat need for multiple chunks test
        .toKorolev
        .flatMap { korolevStream =>
          korolevStream
            .unchunk
            .fold(Vector.empty[Int])((acc, value) => acc :+ value)
            .map(result => result shouldEqual values)
        }
    zio.Unsafe.unsafeCompat { implicit u =>
      runtime.unsafe.runToFuture(io)
    }
  }

}
