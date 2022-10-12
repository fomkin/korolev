package korolev.zio

import zio.{Queue as _, Hub as _, Console as _, _}
import korolev.effect._

import zio.test.Assertion.*
import zio.test.TestAspect.{identity as _, _}
import zio.test.*

object Zio2HubSpec extends ZIOSpecDefault {
  val smallInt: Gen[Sized, Int] =
    Gen.small(Gen.const(_), 1)

  implicit val effect: Zio2Effect[Any, Throwable] = new Zio2Effect[Any, Throwable](runtime, identity, identity)

  def spec =
    suite("Hub Spec")(
      suite("sequential publishers and subscribers")(
        test("with one publisher and one subscriber") {
          check(smallInt, Gen.listOf(smallInt)) {
            (n, as) =>
              for {
                promise1 <- Promise.make[Nothing, Unit]
                promise2 <- Promise.make[Nothing, Unit]
                queue = Queue[Task, Int](n)
                hub = Hub[Task, Int](queue.stream, n)
                subscriber <- ZIO.scoped {
                  hub.newStream().flatMap { subscription =>
                    promise1.succeed(()) *> promise2.await *> ZIO.foreach(as.take(n))(_ => subscription.pull())
                  }
                }.fork
                _ <- promise1.await
                _ <- ZIO.foreach(as.take(n))(queue.offer)
                _ <- promise2.succeed(())
                values <- subscriber.join
              } yield assert(values.flatten)(equalTo(as.take(n)))
          }
        } @@ timeout(10.seconds),
        test("with one publisher and two subscribers") {
          check(smallInt, Gen.listOf(smallInt)) {
            (n, as) =>
              for {
                promise1 <- Promise.make[Nothing, Unit]
                promise2 <- Promise.make[Nothing, Unit]
                promise3 <- Promise.make[Nothing, Unit]
                queue = Queue[Task, Int]()
                hub = Hub[Task, Int](queue.stream)
                // _ <- ZIO.debug(as.take(n))
                subscriber1 <- ZIO.scoped {
                  hub
                    .newStream()
                    .flatMap(subscription =>
                      promise1.succeed(()) *> promise3.await *> ZIO.foreach(as.take(n))(_ => subscription.pull()))
                }.fork
                subscriber2 <- ZIO.scoped {
                  hub
                    .newStream()
                    .flatMap(subscription =>
                      promise2.succeed(()) *> promise3.await *> ZIO.foreach(as.take(n))(_ => subscription.pull()))
                }.fork
                _ <- promise1.await
                _ <- promise2.await
                _ <- ZIO.foreach(as.take(n))(queue.offer)
                _ <- promise3.succeed(())
                values1 <- subscriber1.join
                values2 <- subscriber2.join
              } yield
                assert(values1.flatten)(equalTo(as.take(n))) &&
                  assert(values2.flatten)(equalTo(as.take(n)))
          }
        } @@ timeout(20.seconds)
      ),
      suite("concurrent publishers and subscribers")(
        test("one to one") {
          check(smallInt, Gen.listOf(smallInt)) {
            (n, as) =>
              for {
                promise <- Promise.make[Nothing, Unit]
                queue <- ZIO.succeed(Queue[Task, Int](n))
                hub = Hub[Task, Int](queue.stream, n)
                subscriber <- ZIO.scoped {
                  hub.newStream().flatMap { subscription =>
                    promise.succeed(()) *> ZIO.foreach(as.take(n))(_ => subscription.pull().some)
                  }
                }.fork
                _ <- promise.await
                _ <- ZIO.foreach(as.take(n))(queue.enqueue).fork
                values <- subscriber.join
              } yield assert(values)(equalTo(as.take(n)))
          }
        },
        test("one to many") {
          check(smallInt, Gen.listOf(smallInt)) {
            (n, as) =>
              for {
                promise1 <- Promise.make[Nothing, Unit]
                promise2 <- Promise.make[Nothing, Unit]
                queue <- ZIO.succeed(Queue[Task, Int](n))
                hub = Hub[Task, Int](queue.stream, n)
                subscriber1 <- ZIO.scoped {
                  hub.newStream().flatMap { subscription =>
                    promise1.succeed(()) *> ZIO.foreach(as.take(n))(_ => subscription.pull().some)
                  }
                }.fork
                subscriber2 <- ZIO.scoped {
                  hub.newStream().flatMap { subscription =>
                    promise2.succeed(()) *> ZIO.foreach(as.take(n))(_ => subscription.pull().some)
                  }
                }.fork
                _ <- promise1.await
                _ <- promise2.await
                _ <- ZIO.foreach(as.take(n))(queue.enqueue).fork
                values1 <- subscriber1.join
                values2 <- subscriber2.join
              } yield
                assert(values1)(equalTo(as.take(n))) &&
                  assert(values2)(equalTo(as.take(n)))
          }
        },
        test("many to many") {
          check(smallInt, Gen.listOf(smallInt)) {
            (n, as) =>
              for {
                promise1 <- Promise.make[Nothing, Unit]
                promise2 <- Promise.make[Nothing, Unit]
                queue <- ZIO.succeed(Queue[Task, Int](n * 2))
                hub = Hub[Task, Int](queue.stream, n * 2)
                subscriber1 <- ZIO.scoped {
                  hub.newStream().flatMap { subscription =>
                    promise1.succeed(()) *> ZIO.foreach((as ::: as).take(n * 2))(_ => subscription.pull().some)
                  }
                }.fork
                subscriber2 <- ZIO.scoped {
                  hub.newStream().flatMap { subscription =>
                    promise2.succeed(()) *> ZIO.foreach((as ::: as).take(n * 2))(_ => subscription.pull().some)
                  }
                }.fork
                _ <- promise1.await
                _ <- promise2.await
                _ <- ZIO.foreach(as.take(n))(queue.enqueue).fork
                _ <- ZIO.foreach(as.take(n).map(-_))(queue.enqueue).fork
                values1 <- subscriber1.join
                values2 <- subscriber2.join
              } yield
                assert(values1.filter(_ > 0))(equalTo(as.take(n))) &&
                  assert(values1.filter(_ < 0))(equalTo(as.take(n).map(-_))) &&
                  assert(values2.filter(_ > 0))(equalTo(as.take(n))) &&
                  assert(values2.filter(_ < 0))(equalTo(as.take(n).map(-_)))
          }
        },
        test("with one publisher and three subscribers")(assertZIO {
          for {
            queue <- ZIO.succeed(Queue[Task, Int]())
            hub = Hub[Task, Int](queue.stream)
            s1 <- hub.newStream()
            s2 <- hub.newStream()
            s3 <- hub.newStream()
            ss = Seq(s1, s2, s3)

            fiber <- ZIO
              .foreachPar(ss)(s => ZIO.foreach(1 to 200)(_ => s.pull().some))
              .fork

            _ <- ZIO.foreachDiscard(1 to 200)(a => queue.enqueue(a))
            num <- fiber.join
            _ <- ZIO
              .foreach(num)(nums => {
                val duplicates = nums.groupBy(identity).collect { case (x, List(_, _, _*)) => x }.toSet
                ZIO.debug(
                  nums
                    .map {
                      case num if duplicates.contains(num) => s"${Console.RED}$num${Console.WHITE}"
                      case num                             => num.toString
                    }
                    .mkString(", ")
                ) *> ZIO.debug("")
              })
              .when(p = false)
          } yield num

        }(forall(equalTo(Range.inclusive(1, 200))))) @@ repeat(Schedule.recurs(200))
      ),
      suite("back pressure")(
        test("one to one") {
          check(smallInt, Gen.listOf(smallInt)) {
            (n, as) =>
              for {
                promise <- Promise.make[Nothing, Unit]
                queue <- ZIO.succeed(Queue[Task, Int](n))
                hub = Hub[Task, Int](queue.stream, n)
                subscriber <- ZIO.scoped {
                  hub.newStream().flatMap { subscription =>
                    promise.succeed(()) *> ZIO.foreach(as)(_ => subscription.pull().some)
                  }
                }.fork
                _ <- promise.await
                _ <- ZIO.foreach(as)(queue.enqueue).fork
                values <- subscriber.join
              } yield assert(values)(equalTo(as))
          }
        },
        // this test does not work with bounded hubs
//      test("one to many") {
//        check(smallInt, Gen.listOf(smallInt)) { (n, as) =>
//          for {
//            promise1 <- Promise.make[Nothing, Unit]
//            promise2 <- Promise.make[Nothing, Unit]
//            queue <- ZIO.succeed(Queue[Task, Int](n))
//            hub = Hub[Task, Int](queue.stream, n)
//            subscriber1 <-
//              hub.newStream().flatMap { subscription =>
//                promise1.succeed(()) *> ZIO.foreach(as)(_ => subscription.pull().some)
//              }.fork
//            subscriber2 <-
//              hub.newStream().flatMap { subscription =>
//                promise2.succeed(()) *> ZIO.foreach(as)(_ => subscription.pull().some)
//              }.fork
//            _       <- promise1.await
//            _       <- promise2.await
//            _       <- ZIO.foreach(as)(queue.enqueue).fork
//            values1 <- subscriber1.join.disconnect.timeoutFail(new Exception(s"timeout ${n} ${as}"))(1.second)
//            values2 <- subscriber2.join.disconnect.timeoutFail(new Exception(s"timeout ${n} ${as}"))(1.second)
//          } yield assert(values1)(equalTo(as)) &&
//            assert(values2)(equalTo(as))
//        }
//      },
      )
    ) @@ silent
}
