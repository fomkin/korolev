package korolev.effect

import korolev.effect.Reporter.PrintReporter.Implicit
import org.scalatest.freespec.AsyncFreeSpec

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Random

class AsyncResourcePoolSpec extends AsyncFreeSpec {

  override implicit def executionContext: ExecutionContext =
    ExecutionContext.Implicits.global

  "Create new item if pool is empty" in {
    for {
      pool <- preparePool()
      _ <- pool.borrow()
    } yield {
      assert(pool.unsafeGetState.total == 1)
      assert(pool.unsafeGetState.items.isEmpty)
    }
  }

  "Create new item if all items are borrowed" in {
    for {
      pool <- preparePool()
      firstBorrow <- pool.borrow()
      secondBorrow <- pool.borrow()
    } yield {
      assert(firstBorrow.value != secondBorrow.value)
      assert(pool.unsafeGetState.total == 2)
      assert(pool.unsafeGetState.items.isEmpty)
    }
  }

  "Create new item for a waiter if borrowed item had closed" in {
    for {
      pool <- preparePool()
      firstBorrow <- pool.borrow()
      secondBorrow <- pool.borrow()
      thirdBorrow = pool.borrow()
      _ <- firstBorrow.value.close()
    } yield {
      assert(thirdBorrow.isCompleted)
      assert(pool.unsafeGetState.total == 2)
      assert(pool.unsafeGetState.items.isEmpty)
    }
  }

  "Gives one of idle items if it exists" in {
    for {
      pool <- preparePool()
      firstBorrow <- pool.borrow()
      _ <- firstBorrow.give()
      secondBorrow <- pool.borrow()
    } yield {
      assert(firstBorrow.value eq secondBorrow.value)
      assert(pool.unsafeGetState.total == 1)
      assert(pool.unsafeGetState.items.isEmpty)
    }
  }

  "Return item to pool" in {
    for {
      pool <- preparePool()
      firstBorrow <- pool.borrow()
      _ <- firstBorrow.give()
    } yield {
      assert(pool.unsafeGetState.total == 1)
      assert(pool.unsafeGetState.items.nonEmpty)
    }
  }

  "Keep waiting if all items are borrowed and maxCount exceeded" in {
    for {
      pool <- preparePool()
      firstBorrow <- pool.borrow()
      secondBorrow <- pool.borrow()
      thirdBorrow = pool.borrow()
    } yield {
      assert(!thirdBorrow.isCompleted)
      assert(pool.unsafeGetState.total == 2)
      assert(pool.unsafeGetState.items.isEmpty)
    }
  }

  "Transfer item to next borrower in a queue" in {
    for {
      pool <- preparePool()
      firstBorrow <- pool.borrow()
      _ <- pool.borrow()
      thirdBorrow = pool.borrow()
      _ <- firstBorrow.give()
    } yield {
      assert(thirdBorrow.isCompleted)
      assert(pool.unsafeGetState.total == 2)
      assert(pool.unsafeGetState.items.isEmpty)
    }
  }

  "Do not take back closed items" in {
    for {
      pool <- preparePool()
      firstBorrow <- pool.borrow()
      _ <- firstBorrow.value.close()
      _ <- firstBorrow.give()
    } yield {
      assert(pool.unsafeGetState.total == 0)
      assert(pool.unsafeGetState.items.isEmpty)
    }
  }

  "Close obsolete resources on cleanup" in {
    for {
      clock <- prepareClock()
      pool <- preparePool(maxCount = 5, currentTime = clock.currentNanos)
      borrow1 <- pool.borrow()
      borrow2 <- pool.borrow()
      borrow3 <- pool.borrow()
      borrow4 <- pool.borrow()
      borrow5 <- pool.borrow()
      _ <- borrow1.give()
      _ <- borrow2.give()
      _ <- clock.set(150)
      _ <- borrow3.give()
      _ <- borrow4.give()
      _ <- borrow5.give()
      _ <- pool.cleanup()
    } yield {
      val state = pool.unsafeGetState
      assert(borrow1.value.closed)
      assert(borrow2.value.closed)
      assert(!borrow3.value.closed)
      assert(!borrow4.value.closed)
      assert(!borrow5.value.closed)
      assert(state.total == 3)
      assert(state.items.map(_.value).toSet == Set(borrow3.value, borrow4.value, borrow5.value))
    }
  }

  "Work correctly in concurrent environment" in {
    val pool = preparePoolNow(5)
    val result = new AtomicInteger(0)
    val loops = 12
    val iterations = 30000

    def loop(i: Int): Future[Unit] = {
      if (i > 0) {
        for {
          borrow <- pool.borrow()
          sleepTime = (Random.nextInt(200) + 10).micros
          _ <- Scheduler[Future].sleep(sleepTime)
          _ = result.incrementAndGet()
          _ <- if (Random.nextInt(5000) == 1) borrow.value.close() else Future.unit
          _ <- borrow.give()
          _ <- loop(i - 1)
        } yield ()
      } else {
        Future.unit
      }
    }

    Future.sequence(1.to(loops).map(_ => loop(iterations))).map { _ =>
      assert(result.get == loops * iterations)
    }
  }

  val timeZero: () => Future[Long] =
    () => Future.successful(0)

  def preparePoolNow(maxCount: Int = 2,
                     currentTime: () => Future[Long] = timeZero,
                     maxIdleTime: FiniteDuration = 100.nanos): AsyncResourcePool[Future, Resource] = {
    def factory = Future.successful(new Resource())
    new AsyncResourcePool[Future, Resource]("case", factory, currentTime, maxCount, maxIdleTime)
  }

  def preparePool(maxCount: Int = 2,
                  currentTime: () => Future[Long] = timeZero,
                  maxIdleTime: FiniteDuration = 100.nanos): Future[AsyncResourcePool[Future, Resource]] = {
    Future.successful(preparePoolNow(maxCount, currentTime, maxIdleTime))
  }

  def prepareClock(): Future[Clock] = Future.successful(new Clock)

  class Clock(@volatile var value: Long = 0L) {

    def set(value: Long): Future[Unit] =
      Future.successful(this.value = value)

    def currentNanos(): Future[Long] =
      Future.successful(value)
  }

  class Resource {
    @volatile var closed = false
    @volatile var promises = List.empty[Promise[Unit]]

    def onClose(): Future[Unit] = this.synchronized {
      if (!closed) {
        val newPromise = Promise[Unit]
        promises = newPromise :: promises
        newPromise.future
      } else {
        Future.unit
      }
    }

    def close(): Future[Unit] = this.synchronized {
      val xs = promises
      closed = true
      promises = Nil
      xs.foreach { promise =>
        promise.success(())
      }
      Future.unit
    }
  }

  implicit val resourceClose: Close[Future, Resource] = new Close[Future, Resource] {

    def onClose(that: Resource): Future[Unit] =
      that.onClose()

    def close(that: Resource): Future[Unit] =
      that.close()
  }
}
