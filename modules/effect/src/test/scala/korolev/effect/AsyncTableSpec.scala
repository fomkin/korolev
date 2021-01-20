package korolev.effect

import korolev.effect.AsyncTable.{AlreadyContainsKeyException, RemovedBeforePutException}
import org.scalatest.freespec.AsyncFreeSpec

import scala.concurrent.{ExecutionContext, Future}
import syntax._

class AsyncTableSpec extends AsyncFreeSpec {

  implicit override def executionContext: ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  "put before get" in {
    for {
      table <- AsyncTable.empty[Future, Int, String]
      _ <- table.put(42, "Hello world")
      value <- table.get(42)
    } yield {
      assert(value == "Hello world")
    }
  }

  "put after get" in {
    for {
      table <- AsyncTable.empty[Future, Int, String]
      fiber <- table.get(42).start
      _ <- table.put(42, "Hello world")
      value <- fiber.join()
    } yield {
      assert(value == "Hello world")
    }
  }

  "remove before get" in recoverToSucceededIf[RemovedBeforePutException] {
    for {
      table <- AsyncTable.empty[Future, Int, String]
      fiber <- table.get(42).start
      _ <- table.remove(42)
      _ <- fiber.join()
    } yield ()
  }

  "get twice" in {
    for {
      table <- AsyncTable.empty[Future, Int, String]
      fiber1 <- table.get(42).start
      fiber2 <- table.get(42).start
      _ <- table.put(42, "Hello world")
      value1 <- fiber1.join()
      value2 <- fiber2.join()
    } yield {
      assert(value1 == value2 && value1 == "Hello world")
    }
  }

  "put twice" in recoverToSucceededIf[AlreadyContainsKeyException] {
    for {
      table <- AsyncTable.empty[Future, Int, String]
      _ <- table.put(42, "Hello world")
      _ <- table.put(42, "Hello world")
    } yield ()
  }

}
