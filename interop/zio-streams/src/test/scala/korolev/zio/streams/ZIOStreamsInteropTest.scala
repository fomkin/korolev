package korolev.zio.streams

import org.scalatest.{AsyncFlatSpec, Matchers}
import korolev.effect.{Queue, Effect => KorolevEffect, Stream => KorolevStream}
import zio.{Runtime, Task}
import korolev.zio._
import zio.stream.ZSink

class ZIOStreamsInteropTest  extends AsyncFlatSpec with Matchers {

  "KorolevStream.toZStream" should "provide zio.Stream that contain exactly same values as original Korolev stream" in {

    implicit val runtime = Runtime.default
    implicit val effect = taskEffectInstance[Any](runtime)

    val values = List(1, 2, 3, 4, 5)

    val io = KorolevStream(values: _*)
      .mat[Task]()
      .flatMap { korolevStream =>
        korolevStream
          .toZStream
          .run(ZSink.foldLeft(List.empty[Int]){ case (acc, v) => acc :+ v})
      }

     runtime.unsafeRunToFuture(io).map { result =>
        result shouldEqual values
      }
  }

}
