package korolev.http

import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Flow, Source}
import korolev.data.Bytes
import korolev.data.syntax._
import korolev.effect.{Queue, Stream}
import korolev.http.protocol.WebSocketProtocol.Frame
import korolev.web.PathAndQuery._
import korolev.web.Request.Method
import korolev.web.Response.Status
import korolev.web.{Headers, Request}
import org.scalatest.freespec.AsyncFreeSpec

import java.io.ByteArrayInputStream
import java.net.BindException
import java.util.zip.GZIPInputStream
import scala.concurrent.Future

class HttpClientSpec extends AsyncFreeSpec {

  import HttpClientSpec._

  "HttpClient" - {
    "should properly send GET (content-length: 0) requests" in withServer(helloWorldRoute) { port =>
      for {
        response <- HttpClient[Future, Array[Byte]](
          host = "localhost",
          port = port,
          request = Request(Method.Get, Root / "hello", Nil, Some(0), Stream.empty[Future, Array[Byte]])
        )
        strictResponseBody <- response.body.fold(Array.empty[Byte])(_ ++ _)
        utf8Body = strictResponseBody.asUtf8String
      } yield {
        assert(utf8Body.contains("Hello world") && response.status == Status.Ok)
      }
    }

    "should receive gzipped bodies well" in withServer(gzippedRoute) { port =>
      for {
        response <- HttpClient(
          host = "localhost",
          port = port,
          request = Request(
            Method.Get,
            Root / "gz",
            Vector(Headers.AcceptEncoding -> "gzip"),
            Some(0),
            Stream.empty[Future, Array[Byte]]
          )
        )
        strictResponseBody <- response.body.fold(Array.empty[Byte])(_ ++ _)
        utf8Body = uncompressByteArray(strictResponseBody).asUtf8String
      } yield {
        assert(utf8Body.contains("Hello world") && response.status == Status.Ok)
      }
    }

    "should receive chunked bodies well" in withServer(chunkedRoute) { port =>
      for {
        response <- HttpClient(
          host = "localhost",
          port = port,
          request = Request(
            Method.Get,
            Root / "chunked",
            Vector.empty,
            Some(0),
            Stream.empty[Future, Array[Byte]]
          )
        )
        strictResponseBody <- response.body.fold(Array.empty[Byte])(_ ++ _)
        utf8Body = strictResponseBody.asUtf8String
      } yield {
        assert(utf8Body.contains("123") && response.status == Status.Ok)
      }
    }

    "should properly send/receive WebSocket frames" in withServer(wsEchoRoute) { port =>

      val wsSample1 = Frame.Text(Bytes.wrap("Hello!".getBytes))
      val wsSample2 = Frame.Text(Bytes.wrap("I'm cow!".getBytes))

      for {
        queue <- Future.successful(Queue[Future, Frame[Bytes]]())
        response <- HttpClient.webSocket(
          host = "localhost",
          port = port,
          path = Root / "echo",
          outgoingFrames = queue.stream
        )
        _ <- queue.offer(wsSample1)
        echo1 <- response.body.pull()
        _ <- queue.offer(wsSample2)
        echo2 <- response.body.pull()
        _ <- response.body.cancel()
        _ <- queue.close()
      } yield {
        assert(echo1.contains(wsSample1) && echo2.contains(wsSample2))
      }
    }

  }

}

object HttpClientSpec {

  import akka.actor.typed.ActorSystem
  import akka.actor.typed.scaladsl.Behaviors
  import akka.http.scaladsl.Http
  import akka.http.scaladsl.model._
  import akka.http.scaladsl.server.Directives._

  // Http server stubs

  val helloWorldRoute: Route =
    path("hello") {
      get {
        complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Hello world"))
      }
    }

  val gzippedRoute: Route =
    path("gz") {
      get {
        encodeResponse {
          complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Hello world"))
        }
      }
    }

  val chunkedRoute: Route =
    path("chunked") {
      get {
        val xs = Vector(
          HttpEntity.ChunkStreamPart("1"),
          HttpEntity.ChunkStreamPart("2"),
          HttpEntity.ChunkStreamPart("3"),
        )
        complete(HttpEntity.Chunked(ContentTypes.`text/plain(UTF-8)`, Source(xs)))
      }
    }

  val wsEchoRoute: Route = {
    def echo: Flow[Message, Message, Any] =
      Flow[Message].mapConcat {
        case _: BinaryMessage => Nil
        case tm: TextMessage =>
          TextMessage(tm.textStream) :: Nil
      }
    path("echo") {
      handleWebSocketMessages(echo)
    }
  }

  // Utils

  def uncompressByteArray(from: Array[Byte]): Array[Byte] = {
    val stream = new GZIPInputStream(new ByteArrayInputStream(from.asArray), from.length)
    val buffer = new Array[Byte](50)
    var result = new Array[Byte](0)
    var n = 0
    do {
      n = stream.read(buffer)
      if (n > 0)
        result = result ++ buffer.slice(0, n)
    } while (n > 0)
    result
  }

  def withServer[T](route: Route)(f: Int => Future[T]): Future[T] = {

    implicit val system = ActorSystem(Behaviors.empty, "http-client-test-system")
    implicit val executionContext = system.executionContext

    def tryBind(retries: Int) = {
      def nextPort = util.Random.nextInt(64000) + 1000 // select port 1000..64000
      def aux(port: Int, retries: Int, portAttempts: List[Int]): Future[(Int, Http.ServerBinding)] = retries match {
        case 0 => Future.failed(new Exception(s"Unable to bind HTTP server. Tried ports: [${portAttempts.mkString(", ")}]"))
        case _ => Http()
          .newServerAt("localhost", port)
          .bind(route)
          .map(binding => (port, binding))
          .recoverWith { case _: BindException => aux(nextPort, retries - 1, port :: portAttempts) }
      }
      aux(nextPort, retries, Nil)
    }

    tryBind(5).flatMap { case (port, binding) =>
      f(port).transformWith { tryResult =>
        for {
          _ <- binding.unbind()
          _ = system.terminate()
          result <- Future.fromTry(tryResult)
        } yield result
      }
    }
  }
}