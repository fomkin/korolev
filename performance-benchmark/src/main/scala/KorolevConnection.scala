import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.`Set-Cookie`
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.scaladsl.{Keep, Sink, Source, SourceQueue}
import akka.stream.{ActorMaterializer, KillSwitch, KillSwitches, OverflowStrategy}
import akka.typed._
import akka.typed.scaladsl.Actor
import akka.typed.scaladsl.adapter._
import akka.util.ByteString
import akka.{Done, actor}
import data._
import pushka.Ast
import pushka.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.matching.Regex
import scala.util.{Failure, Success}

object KorolevConnection {

  def apply(host: String,
            port: Int,
            maybePath: Option[String],
            ssl: Boolean)
           (receiver: ActorRef[FromServer]): Behavior[ToServer] = {

    val escapedPath = maybePath
      .map { case s if !s.endsWith("/") => s + "/"; case s => s }
      .map { case s if s.startsWith("/") => s.stripPrefix("/"); case s => s }
      .getOrElse("/")

    Actor.deferred[ToServer] { ctx =>

      implicit val untypedSystem: actor.ActorSystem = ctx.system.toUntyped
      implicit val materializer: ActorMaterializer = ActorMaterializer()

      def openPage(): Future[Either[Error, ConnectionInfo]] = {
        val protocol = if (ssl) "https" else "http"
        Http().singleRequest(HttpRequest(uri = s"$protocol://$host:$port$escapedPath")).flatMap {
          case HttpResponse(StatusCodes.OK, headers, entity, _) =>
            entity.dataBytes.runFold(ByteString(""))(_ ++ _) map { body =>
              for {
                deviceId <- headers
                  .collectFirst { case c: `Set-Cookie` if c.cookie.name == "device" => c.cookie.value }
                  .fold[Either[Error, String]](Left(Error.DeviceIdNotDefined))(Right(_))
                sessionId <- SessionIdExtractor
                  .unapplySeq(body.utf8String)
                  .flatMap(_.headOption)
                  .fold[Either[Error, String]](Left(Error.SessionIdNotDefined))(Right(_))
              } yield {
                ConnectionInfo(deviceId, sessionId)
              }
            }
          case resp @ HttpResponse(code, _, _, _) =>
            resp.discardEntityBytes()
            Future.successful(Left(Error.InvalidHttpStatusCodeForPage(code.intValue())))
        }
      }

      def establishConnection(connectionInfo: ConnectionInfo): Future[Either[Error, Connection]] = {
        val (((outgoing, upgradeResponse), killSwitch), closed) = {
          val incoming: Sink[Message, Future[Done]] = {
            def process(message: String): FromServer = {
              val json = read[List[Ast]](message)
              FromServer.Procedure.fromJson(json).right.get
            }
            Sink.foreach[Message] {
              case message: TextMessage.Strict =>
                receiver ! process(message.text)
              case message: TextMessage.Streamed => message
                .textStream
                .runFold("")(_ + _)
                .map(process)
                .foreach(receiver ! _)
              case _ =>
                // ignore
            }
          }

          val protocol = if (ssl) "wss" else "ws"
          val deviceId = connectionInfo.deviceId
          val sessionId = connectionInfo.sessionId
          val uri = s"$protocol://$host:$port${escapedPath}bridge/web-socket/$deviceId/$sessionId"
          println(uri)
          Source.queue[Message](1024, OverflowStrategy.backpressure)
            .viaMat(Http().webSocketClientFlow(WebSocketRequest(uri)))(Keep.both)
            .viaMat(KillSwitches.single)(Keep.both)
            .toMat(incoming)(Keep.both)
            .run()
        }

        upgradeResponse.map {
          case ValidUpgrade(_, _) => Right(Connection(outgoing, killSwitch, closed))
          case InvalidUpgradeResponse(response, _) => Left(Error.InvalidHttpStatusCodeForWS(response.status.intValue()))
        }
      }

      val worker = ctx.spawnAnonymous {
        Actor.immutable[Either[ToServer, Connection]] {
          case (_, Right(connection)) =>
            receiver ! FromServer.Connected(ctx.self)
            connection.closed foreach { _ =>
              // Stop actor when connection closed by peer
              ctx.stop(ctx.self)
            }
            // Start receive messages to server
            Actor.immutable[Either[ToServer, Connection]] {
              case (_, Left(ToServer.Callback(tpe, data))) =>
                val json = write((tpe.code, data))
                connection.outgoing.offer(TextMessage(json))
                Actor.same
              case (_, Left(ToServer.Close)) =>
                connection.killSwitch.shutdown()
                Actor.same
              case _ => Actor.ignore
            } onSignal {
              case (_, Terminated(_)) ⇒
                // Close connection when actor was stopped
                connection.killSwitch.shutdown()
                Actor.stopped
            }
          case _ => Actor.ignore
        }
      }

      openPage()
        .flatMap {
          case Right(connectionInfo) => establishConnection(connectionInfo)
          case Left(error) => Future.successful(Left(error))
        }
        .onComplete {
          case Success(Right(connection)) => worker ! Right(connection)
          case Success(Left(error)) => receiver ! FromServer.ErrorOccurred(error)
          case Failure(e) => receiver ! FromServer.ErrorOccurred(Error.ArbitraryThrowable(e))
        }

      Actor.immutable[ToServer] { (_, message) =>
        worker ! Left(message)
        Actor.same
      }
    }
  }

  val SessionIdExtractor: Regex = """(?s).*window\['kfg'\]=\{sid:'(.+)',r.*""".r

  case class ConnectionInfo(deviceId: String, sessionId: String)
  case class Connection(outgoing: SourceQueue[Message], killSwitch: KillSwitch, closed: Future[_])
}
