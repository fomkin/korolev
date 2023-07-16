import korolev.pekko.util.Countdown
import korolev.effect.Effect.FutureEffect
import korolev.effect.syntax._
import org.scalatest.freespec.AsyncFreeSpec

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Future

class CountdownSpec extends AsyncFreeSpec {

  private case class Promise() extends Exception

  class ThrowOnPromise(var switch: Boolean = false) extends FutureEffect {

    def toggle(): Future[Unit] = Future.successful {
      switch = !switch
    }

    override def promise[A](cb: (Either[Throwable, A] => Unit) => Unit): Future[A] = {
      var released = false
      val p = super.promise { (f: Either[Throwable, A] => Unit) =>
        cb { x =>
          released = true
          f(x)
        }
      }
      if (switch) {
        if (released) p
        else Future.failed(Promise())
      } else {
        p
      }
    }
  }

  "decOrLock should lock when count is 0" in recoverToSucceededIf[Promise] {
    val countdown = new Countdown[Future]()(new ThrowOnPromise(switch = true))
    for {
      _ <- countdown.add(3)
      _ <- countdown.decOrLock()
      _ <- countdown.decOrLock()
      _ <- countdown.decOrLock()
      _ <- countdown.decOrLock() // should lock
    } yield ()
  }

  "decOrLock should not lock until count > 0" in {
    val countdown = new Countdown[Future]()(new ThrowOnPromise)
    for {
      _ <- countdown.add(3)
      _ <- countdown.decOrLock()
      _ <- countdown.decOrLock()
      _ <- countdown.decOrLock()
    } yield succeed
  }

  "add should release taken lock" in {
    val effectInstance = new ThrowOnPromise(switch = false)
    val countdown = new Countdown[Future]()(effectInstance)
    val result = new AtomicReference[String]("")
    for {
      _ <- countdown.add(3)
      _ <- countdown.decOrLock()
      _ <- countdown.decOrLock()
      _ <- countdown.decOrLock()
      fiber <- countdown
        .decOrLock() // should lock
        .map(_ => result.getAndUpdate(_ + " world"))
        .start
      _ = result.set("hello")
      _ <- countdown.add(3)
      _ <- fiber.join()
    } yield assert(result.get == "hello world")
  }

  "count the lock released by the add invocation" in recoverToSucceededIf[Promise] {
    val effectInstance = new ThrowOnPromise(switch = false)
    val countdown = new Countdown[Future]()(effectInstance)
    for {
      _ <- countdown.add(3)
      _ <- countdown.decOrLock()
      _ <- countdown.decOrLock()
      _ <- countdown.decOrLock()
      fiber <- countdown
        .decOrLock() // should lock
        .start
      _ <- countdown.add(3)
      _ <- fiber.join()
      _ <- effectInstance.toggle()
      _ <- countdown.decOrLock()
      _ <- countdown.decOrLock()
      _ <- countdown.decOrLock() // should lock
    } yield ()
  }

}
