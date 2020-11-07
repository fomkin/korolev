package korolev

import akka.Done
import akka.actor.ActorSystem
import akka.actor.typed.{ActorRef, Behavior, Terminated}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.`Set-Cookie`
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.scaladsl.{Keep, Sink, Source, SourceQueue}
import akka.stream.{KillSwitch, KillSwitches, OverflowStrategy}
import akka.util.ByteString
import korolev.data._
import korolev.state.{DeviceId, SessionId}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.matching.Regex
import scala.util.{Failure, Success}
import korolev.server.internal.Cookies
import ujson._

object KorolevConnection {

  def apply(host: String, port: Int, maybePath: Option[String], ssl: Boolean)(
    receiver: ActorRef[FromServer])(implicit untypedSystem: ActorSystem): Behavior[ToServer] = {

    val escapedPath = maybePath
      .map { case s if !s.endsWith("/") => s + "/"; case s => s }
      .map { case s if s.startsWith("/") => s.stripPrefix("/"); case s => s }
      .getOrElse("/")

    Behaviors.setup[ToServer] { ctx =>
      val bufferedReceiver = {
        def aux(stash: List[FromServer]): Behavior[FromServer] = {
          Behaviors.receiveMessage[FromServer] {
            case FromServer.Closed =>
              aux(FromServer.Closed :: stash)
            case procedure: FromServer.Procedure =>
              aux(procedure :: stash)
            case message =>
              receiver ! message
              stash.reverse.foreach(receiver ! _)
              Behaviors.receiveMessage { message =>
                receiver ! message
                Behaviors.same
              }
          }
        }

        ctx.spawnAnonymous(aux(Nil))
      }

      def openPage(): Future[Either[Error, ConnectionInfo]] = {
        val protocol = if (ssl) "https" else "http"
        Http().singleRequest(HttpRequest(uri = s"$protocol://$host:$port$escapedPath")).flatMap {
          case HttpResponse(StatusCodes.OK, headers, entity, _) =>
            entity.dataBytes.runFold(ByteString(""))(_ ++ _) map { body =>
              for {
                deviceId <- headers
                  .collectFirst { case c: `Set-Cookie` if c.cookie.name == Cookies.DeviceId => c.cookie.value }
                  .fold[Either[Error, String]](Left(Error.DeviceIdNotDefined))(Right(_))
                sessionId <- SessionIdExtractor
                  .unapplySeq(body.utf8String)
                  .flatMap(_.headOption)
                  .fold[Either[Error, String]](Left(Error.SessionIdNotDefined))(Right(_))
              } yield {
                ConnectionInfo(deviceId, sessionId)
              }
            }
          case resp@HttpResponse(code, _, _, _) =>
            resp.discardEntityBytes()
            Future.successful(Left(Error.InvalidHttpStatusCodeForPage(code.intValue())))
        }
      }

      def establishConnection(connectionInfo: ConnectionInfo): Future[Either[Error, Connection]] = {
        val (((outgoing, upgradeResponse), killSwitch), closed) = {
          val incoming: Sink[Message, Future[Done]] = {
            def process(message: String): FromServer = {
              val json: Value = read(message)
              FromServer.Procedure.fromJson(json).right.get
            }

            Sink.foreach[Message] {
              case message: TextMessage.Strict =>
                bufferedReceiver ! process(message.text)
              case message: TextMessage.Streamed =>
                message.textStream
                  .runFold("")(_ + _)
                  .map(process)
                  .foreach(bufferedReceiver ! _)
              case _ =>
              // ignore
            }
          }

          val protocol = if (ssl) "wss" else "ws"
          val deviceId = connectionInfo.deviceId
          val sessionId = connectionInfo.sessionId
          val uri = s"$protocol://$host:$port${escapedPath}bridge/web-socket/$deviceId/$sessionId"

          Source
            .queue[Message](1024, OverflowStrategy.backpressure)
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
        Behaviors.receiveMessage[Either[ToServer, Connection]] {
          case Right(connection) =>
            connection.closed foreach { _ =>
              // Stop actor when connection closed by peer
              ctx.stop(ctx.self)
            }
            // Start receive messages to server
            Behaviors.receiveMessage[Either[ToServer, Connection]] {
              case Left(ToServer.Callback(tpe, Some(data))) =>
                val json = write(ujson.Arr(tpe.code, data))
                connection.outgoing.offer(TextMessage(json))
                Behaviors.same

              case Left(ToServer.Callback(tpe, None)) =>
                val json = write(ujson.Arr(tpe.code))
                connection.outgoing.offer(TextMessage(json))
                Behaviors.same

              case Left(ToServer.Close) =>
                connection.killSwitch.shutdown()
                Behaviors.same
              case _ => Behaviors.ignore
            }.receiveSignal {
              case (_, Terminated(_)) ⇒
                // Close connection when actor was stopped
                connection.killSwitch.shutdown()
                Behaviors.stopped
            }
          case _ =>
            Behaviors.ignore
        }
      }

      openPage()
        .flatMap {
          case Right(connectionInfo) => establishConnection(connectionInfo)
          case Left(error) => Future.successful(Left(error))
        }
        .onComplete {
          case Success(Right(connection)) =>
            bufferedReceiver ! FromServer.Connected(ctx.self)
            worker ! Right(connection)
          case Success(Left(error)) => bufferedReceiver ! FromServer.ErrorOccurred(error)
          case Failure(e) => bufferedReceiver ! FromServer.ErrorOccurred(Error.ArbitraryThrowable(e))
        }

      Behaviors.receiveMessage[ToServer] { message =>
        worker ! Left(message)
        Behaviors.same
      }
    }
  }

  val SessionIdExtractor: Regex = """(?s).*window\['kfg'\]=\{sid:'(.+)',r.*""".r

  case class ConnectionInfo(deviceId: DeviceId, sessionId: SessionId)

  case class Connection(outgoing: SourceQueue[Message], killSwitch: KillSwitch, closed: Future[_])

}