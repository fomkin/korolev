import java.nio.channels.AsynchronousChannelGroup
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

import fs2._
import pushka.Ast
import scodec.bits.ByteVector
import spinoco.fs2.http
import spinoco.fs2.http._
import spinoco.fs2.http.websocket._
import spinoco.protocol.http._
import spinoco.protocol.http.header._
import KorolevIncomingMessage._
import Reaction._
import Execution._
import fs2.async.mutable.Signal

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
object PerformanceBenchmark extends App {

  val host = "localhost"
  val port = 8181
  val concurrentClientsNum = 30
  val actionsNumber = 50
  def actionDelay = (0.5 + Random.nextDouble * 1).seconds

  final val SessionIdPattern = """(?s).*var KorolevSessionId = '(\w+)';.*""".r

  case class KorolevSession(deviceId: String, sessionId: String)
  case class Report(minT: Long = Long.MaxValue, maxT: Long = Long.MinValue, ts: List[Long] = List.empty)

  sealed trait ScenarioPhase
  case object Init extends ScenarioPhase
  case class TabClick(tabNum: Int, started: Long) extends ScenarioPhase

  def currentTime: Long = System.currentTimeMillis

  def pipeline(jobNum: Int, reports: Signal[Task, List[Report]]): Pipe[Task, Frame[String], Frame[String]] = { inboundStream =>
    val state = for {
      queue     <- async.unboundedQueue[Task, Frame[String]]
      phase     <- async.signalOf[Task, ScenarioPhase](Init)
      eventCb   <- async.signalOf[Task, String]("")
      report    <- async.signalOf[Task, Report](Report())
    } yield {
      (queue, phase, eventCb, report)
    }
    Stream.eval(state) flatMap {
      case (outboundQueue, phase, eventCb, report) =>
        // Send message to server
        def send(xs: Any*) = {
          val s = encodeKorolevMessage(xs:_*)
          outboundQueue.enqueue1(Frame.Text(s))
        }
        inboundStream
          // Parse message from server
          .collect { case Frame.Text(s) => s }
          .map(s => decodeKorolevMessage(s))
          .flatMap(Stream.apply(_:_*))
          // Reaction on server messages
          .evalMap { message =>
            phase.get flatMap {
              case Init =>
                reaction(message) {
                  case FunctionCall(id, _, "RegisterGlobalEventHandler", Ast.Str(link) :: Nil) =>
                    eventCb
                      .set(link.stripPrefix("@link:"))
                      .map(_ => Seq(id, true, "@unit"))
                  case FunctionCall(id, _, "ListenEvent", Ast.Str("click") :: _) =>
                    eventCb.get flatMap { cb =>
                      phase
                        .set(TabClick(1, currentTime))
                        .flatMap(_ => send(-1, cb, "0:0_1_1:click"))
                        .map(_ => Seq(id, true, "@unit"))
                    }
                }
              case TabClick(tabNum, startTime) =>
                reaction(message) {
                  case FunctionCall(id, _, "SetRenderNum", Ast.Num(rNum) :: Nil) =>
                    val nextTabNum = tabNum match {
                      case 0 => 1
                      case 1 => 2
                      case 2 => 0
                    }
                    val dt = currentTime - startTime
                    for {
                      _ <- report.modify { r =>
                        r.copy(
                          minT = Math.min(r.minT, dt),
                          maxT = Math.max(r.maxT, dt),
                          ts = dt :: r.ts
                        )
                      }
                      cb <- eventCb.get
                      _ <- Task.schedule((), actionDelay)
                      _ <- send(-1, cb, s"$rNum:0_1_$nextTabNum:click")
                      _ <- phase.set(TabClick(nextTabNum, currentTime))
                    } yield Seq(id, true, "@unit")
                }
            }
          }
          // Push messages to server
          .evalMap(send(_:_*))
          // Combine input and output
          .mergeDrainL(outboundQueue.dequeue)
          .interruptWhen(report.map(_.ts.length > actionsNumber))
          .onFinalize {
            report.get flatMap { r =>
              reports
                .modify(r :: _)
                .map(_ => println(s"job #$jobNum finished"))
            }
          }
    }
  }

  def runTest(jobNum: Int, reports: Signal[Task, List[Report]], client: HttpClient[Task]) = {
    println(s"job #$jobNum started")
    val request = HttpRequest.get[Task](Uri.http(host, port, "/"))
    client.request(request).flatMap { response =>
      response.body
        .fold(ByteVector.empty)(_ :+ _)
        .map(_.decodeUtf8) flatMap {
        case Right(body) =>
          val sessionOpt = response.header.headers.collectFirst {
            case `Set-Cookie`(cookie) if cookie.name == "device" =>
              val SessionIdPattern(sessionId) = body
              KorolevSession(cookie.content, sessionId)
          }
          sessionOpt.fold(Stream.empty[Task, Unit]) {
            case KorolevSession(deviceId, sessionId) =>
              val request = WebSocketRequest.ws(host, port, s"/bridge/web-socket/$deviceId/$sessionId")
              client
                .websocket(request, pipeline(jobNum, reports))
                .map(_ => ())
          }
        case Left(exception) => throw exception
      }
    }.runLog
  }

  val mainTask = for {
    reportsSignal <- async.signalOf[Task, List[Report]](List.empty)
    client  <- http.client[Task]()
    _ <- Task.parallelTraverse(1 to concurrentClientsNum)(n => runTest(n, reportsSignal, client))
    reports <- reportsSignal.get
  } yield {
    println("Report aggregation...")
    val summary = reports.foldLeft(Report()) {
      case (reportAcc, nextReport) =>
        reportAcc.copy(
          minT = Math.min(reportAcc.minT, nextReport.minT),
          maxT = Math.max(reportAcc.maxT, nextReport.maxT),
          ts = nextReport.ts ::: reportAcc.ts
        )
    }
    val median = {
      val xs = summary.ts.sorted
      xs(xs.length / 2)
    }
    s"min: ${summary.minT}, max: ${summary.maxT}, median: $median"
  }

  println(mainTask.unsafeRun())
  System.exit(0)
}
