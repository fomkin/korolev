package korolev.zio

import zio.{Queue as _, _}
import korolev.effect._

import zio.test.Assertion._
import zio.test.TestAspect.{identity as _, _}
import zio.test._

import QueueSpecUtil._

object Zio2QueueSpec extends ZIOSpecDefault {
  implicit val effect: Zio2Effect[Any, Throwable] = new Zio2Effect[Any, Throwable](runtime, identity, identity)
  def spec = suite("Queue Spec")(
    test("sequential offer and take") {
      for {
        _ <- ZIO.unit
        queue = Queue[Task, Int]()
        o1 <- queue.offer(10)
        v1 <- queue.stream.pull().some
        o2 <- queue.offer(20)
        v2 <- queue.stream.pull().some
      } yield
        assert(v1)(equalTo(10)) &&
          assert(v2)(equalTo(20)) &&
          assert(o1)(isTrue) &&
          assert(o2)(isTrue)
    },
    test("sequential take and offer") {
      for {
        _ <- ZIO.unit
        queue = Queue[Task, String]()
        f1 <- queue.stream.pull().some.zipWith(queue.stream.pull().some)(_ + _).fork
        _ <- queue.offer("don't ") *> queue.offer("give up :D")
        v <- f1.join
      } yield assert(v)(equalTo("don't give up :D"))
    },
    // this test does not work because when several cb we give a value to all but not one
//    test("parallel takes and sequential offers ") {
//      for {
//        _ <- ZIO.unit
//        queue = Queue[Task, Int]()
//        f <- ZIO.forkAll(List.fill(10)(queue.stream.pull().some))
//        values = Range.inclusive(1, 10).toList
//        _ <- ZIO.foreachDiscard(values)(queue.offer)
//        v <- f.join
//      } yield assert(v.toSet)(equalTo(values.toSet))
//    },
    test("parallel offers and sequential takes") {
      for {
        _ <- ZIO.unit
        queue = Queue[Task, Int]()
        values = Range.inclusive(1, 10).toList
        f <- ZIO.forkAll(values.map(queue.offer))
        _ <- waitForSize(queue, 10)
        out <- Ref.make[List[Int]](Nil)
        _ <- queue.stream.pull().some.flatMap(i => out.update(i :: _)).repeatN(9)
        l <- out.get
        _ <- f.join
      } yield assert(l.toSet)(equalTo(values.toSet))
    },
    test("offers are suspended by back pressure") {
      for {
        _ <- ZIO.unit
        queue = Queue[Task, Int](10)
        _ <- queue.offer(1).repeatN(9)
        refSuspended <- Ref.make[Boolean](true)
        f <- (queue.enqueue(2) *> refSuspended.set(false)).fork
        _ <- waitForSize(queue, 11)
        isSuspended <- refSuspended.get
        _ <- f.interrupt
      } yield assertTrue(isSuspended)
    },
    test("back pressured offers are retrieved") {
      for {
        _ <- ZIO.unit
        queue = Queue[Task, Int](5)
        values = Range.inclusive(1, 10).toList
        f <- ZIO.forkAll(values.map(queue.enqueue))
        _ <- waitForSize(queue, 10)
        out <- Ref.make[List[Int]](Nil)
        _ <- queue.stream.pull().some.flatMap(i => out.update(i :: _)).repeatN(9)
        l <- out.get
        _ <- f.join
      } yield assert(l.toSet)(equalTo(values.toSet))
    },
    // these tests do not work because pulling and offerring are uninterruptable
    // test("take interruption") {
    //   for {
    //     _            <- ZIO.unit
    //     queue        = Queue[Task, Int]()
    //     f     <- queue.stream.pull().fork
    //     _     <- waitForSize(queue, -1)
    //     _     <- f.interrupt
    //     size  <- queue.size()
    //   } yield assert(size)(equalTo(0))
    // },
    // test("offer interruption") {
    //   for {
    //             _            <- ZIO.unit
    //     queue        = Queue[Task, Int](2)
    //     _     <- queue.enqueue(1)
    //     _     <- queue.enqueue(1)
    //     f     <- queue.enqueue(1).fork
    //     _     <- waitForSize(queue, 3)
    //     _     <- f.interrupt
    //     size  <- queue.size()
    //   } yield assert(size)(equalTo(2))
    // },
    test("queue is ordered") {
      for {
        _ <- ZIO.unit
        queue = Queue[Task, Int]()
        _ <- queue.offer(1)
        _ <- queue.offer(2)
        _ <- queue.offer(3)
        v1 <- queue.stream.pull().some
        v2 <- queue.stream.pull().some
        v3 <- queue.stream.pull().some
      } yield
        assert(v1)(equalTo(1)) &&
          assert(v2)(equalTo(2)) &&
          assert(v3)(equalTo(3))
    },
    test("many to many") {
      check(smallInt, Gen.listOf(smallInt)) { (n, as) =>
        for {
          _ <- ZIO.unit
          queue = Queue[Task, Int]()
          offerors <- ZIO.foreach(as)(a => queue.enqueue(a).fork)
          takers <- ZIO.foreach(as)(_ => queue.stream.pull().some.fork)
          _ <- ZIO.foreach(offerors)(_.join)
          _ <- ZIO.foreach(takers)(_.join)
        } yield assertCompletes
      }
    } @@ samples(200),
    test("returns elements in the correct order") {
      check(Gen.chunkOf(Gen.int(-10, 10))) { as =>
        for {
          _ <- ZIO.unit
          queue = Queue[Task, Int](100)
          f <- ZIO.foreach(as)(queue.offer).fork
          bs <- ZIO.foreach(1 to as.length)(_ => queue.stream.pull().some)
          _ <- f.interrupt
        } yield assert(as)(equalTo(bs))
      }
    },
    test("Concurrent offer/pull invocations")(
      assertZIO(
        for {
          s1 <- Stream(0 until 100: _*).mat()
          s2 <- Stream(100 until 200: _*).mat()
          s3 <- Stream(200 until 300: _*).mat()
          queue = Queue[Task, Int]()

          ss = Seq(s1, s2, s3)
          fiber <- queue.stream.fold(0)((c, _) => c + 1).fork

          _ <- ZIO.foreachParDiscard(ss)(s => s.foreach(v => queue.offer(v).unit))
          _ <- queue.stop()
          count <- fiber.join
        } yield count
      )(equalTo(300))
    ),
   test("canOffer")(
      assertZIO {
        val queue = Queue[Task, Int](2)
        for {
          _ <- ZIO.foreachParDiscard(Seq(1, 2))(queue.offer).fork
          _ <- waitForSize(queue, 2)
          canOffer <- queue.canOffer.fork
          _ <- queue.stream.pull().repeatN(1)
          _ <- canOffer.join
        } yield ()
      }(isUnit)
    ) @@ diagnose(5.seconds)
  )
}

object QueueSpecUtil {
  def waitForValue[T](ref: Task[T], value: T): ZIO[Live, Throwable, T] =
    Live.live((ref <* Clock.sleep(10.millis)).repeatUntil(_ == value))

  def waitForSize[A](queue: Queue[Task, A], size: Int): ZIO[Live, Throwable, RuntimeFlags] =
    waitForValue(queue.size(), size)

  val smallInt: Gen[Sized, Int] =
    Gen.small(Gen.const(_), 1)
}
