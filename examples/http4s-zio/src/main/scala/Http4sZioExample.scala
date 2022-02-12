import org.http4s.{HttpApp, HttpRoutes, Request, Response}
import org.http4s.blaze.server.BlazeServerBuilder
import zio.clock.Clock
import cats.effect.{ExitCode => CatsExitCode, _}
import korolev.http4s
import zio.{App, Task, RIO, Runtime, ZEnv, ZIO, ExitCode => ZExitCode}
import zio.interop.catz._
import korolev.Context
import korolev.server.{KorolevServiceConfig, StateLoader}
import korolev.effect.{Effect => KEffect}
import korolev.zio.zioEffectInstance
import korolev.state.javaSerialization._
import org.http4s.server.Router
import org.http4s.implicits._
import zio.blocking.Blocking

import scala.concurrent.ExecutionContext

object Http4sZioExample extends App {

  type AppTask[A] = RIO[ZEnv, A]

  private class Service()(implicit runtime: Runtime[ZEnv])  {

    import levsha.dsl._
    import html._
    import scala.concurrent.duration._

    implicit val ec: ExecutionContext = runtime.platform.executor.asEC
    implicit val effect: KEffect[AppTask] = zioEffectInstance[ZEnv, Throwable](runtime)(identity)(identity)

    val ctx = Context[ZIO[ZEnv, Throwable, *], Option[Int], Any]

    import ctx._


    def config = KorolevServiceConfig [AppTask, Option[Int], Any] (
      stateLoader = StateLoader.default(Option.empty[Int]),
      rootPath = "/",
      document = {
        case Some(n) => optimize {
          Html(
            body(
              delay(3.seconds) { access =>
                access.transition {
                  case _ => None
                }
              },
              button(
                "Push the button " + n,
                event("click") { access =>
                  access.transition {
                    case s => s.map(_ + 1)
                  }
                }
              ),
              "Wait 3 seconds!"
            )
          )
        }
        case None => optimize {
          Html(
            body(
              button(
                event("click") { access =>
                  access.transition { _ => Some(1) }
                },
                "Push the button"
              )
            )
          )
        }
      }
    )

    def route(): ZIO[ZEnv, Throwable, HttpRoutes[AppTask]] = {
      ZIO(http4s.http4sKorolevService(config))
    }
  }

  private def getAppRoute(): ZIO[ZEnv, Throwable, HttpRoutes[AppTask]] = {
    ZIO.runtime[ZEnv].flatMap { implicit rts =>
      new Service().route()
    }
  }

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ZExitCode] = {

    val prog = for {
      appRoute <- getAppRoute()
      httpApp = Router[AppTask]("/" -> appRoute).orNotFound
      _   <- runHttp(httpApp, 8088)
    } yield ZExitCode.success

    prog.orDie
  }

  def runHttp[R <: Clock with Blocking](
                           httpApp: HttpApp[RIO[R, *]],
                           port: Int
                         )= {
    type Task[A] = ZIO[R, Throwable, A]
    ZIO.runtime[R].flatMap { implicit r =>
      BlazeServerBuilder[Task]
        .withExecutionContext(ExecutionContext.global)
        .bindHttp(port, "0.0.0.0")
        .withHttpApp(httpApp)
        .serve
        .compile[Task, Task, CatsExitCode]
        .drain
    }
  }
}
