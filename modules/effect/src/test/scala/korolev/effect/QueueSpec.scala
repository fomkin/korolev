package korolev.effect

import org.scalatest.Assertion
import org.scalatest.freespec.AsyncFreeSpec
import korolev.effect.syntax._

import scala.concurrent.{ExecutionContext, Future}

class QueueSpec extends AsyncFreeSpec {

  implicit override def executionContext: ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  "Concurrent offer/pull invocations" in {
    val queue = Queue[Future, Int]()

    for {
      s1 <- Stream(0 until 100:_*).mat()
      s2 <- Stream(100 until 200:_*).mat()
      s3 <- Stream(200 until 300:_*).mat()
      ss = Seq(s1, s2, s3)
      _ <- Future.sequence(ss.map(s => Effect[Future].fork(s.foreach(i => queue.offer(i).unit))))
      _ <- queue.close()
    } yield ()

    queue
      .stream
      .fold(0)((x, _) => x + 1)
      .map(count => assert(count == 300))
  }

  ".canOffer" - {

    def checkCanOffer(maxSize: Int): Future[Assertion] = {
      val queue = Queue[Future, Int](maxSize)

      for {
        s1 <- Stream(0 until 100:_*).mat()
        s2 <- Stream(100 until 200:_*).mat()
        s3 <- Stream(200 until 300:_*).mat()
        ss = Seq(s1, s2, s3)
        fs = ss.map { s =>
          Effect[Future].fork {
            s.foreach { i =>
              def aux(): Future[Unit] = queue.offer(i) flatMap {
                case false => queue.canOffer *> aux()
                case true => Future.unit
              }
              aux()
            }
          }
        }
        _ <- Future.sequence(fs)
        _ <- queue.close()
      } yield ()

      queue
        .stream
        .fold(0)((x, _) => x + 1)
        .map(count => assert(count == 300))
    }

    "maxSize = 1" in checkCanOffer(1)
    "maxSize = 5" in checkCanOffer(5)
    "maxSize = 10" in checkCanOffer(10)
  }

}
