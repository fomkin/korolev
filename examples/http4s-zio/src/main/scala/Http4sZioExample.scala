import scala.concurrent.ExecutionContext

import cats.effect.{ExitCode as CatsExitCode, _}
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.{HttpApp, HttpRoutes}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.interop.catz._
import zio.{App, RIO, Runtime, Task, ZEnv, ZIO, ExitCode as ZExitCode}

import korolev.effect.Effect as KEffect
import korolev.server.{KorolevServiceConfig, StateLoader}
import korolev.state.javaSerialization._
import korolev.web.PathAndQuery
import korolev.zio.zioEffectInstance
import korolev.{Context, http4s}

object Http4sZioExample extends App {

  type AppTask[A] = RIO[ZEnv, A]

  private class Service()(implicit runtime: Runtime[ZEnv])  {

    import scala.concurrent.duration._

    import levsha.dsl._
    import html._

    implicit val ec: ExecutionContext = runtime.platform.executor.asEC
    implicit val effect: KEffect[AppTask] = zioEffectInstance[ZEnv, Throwable](runtime)(identity)(identity)

    val ctx = Context[ZIO[ZEnv, Throwable, *], Option[Int], Any]

    import ctx._


    def config = KorolevServiceConfig [AppTask, Option[Int], Any] (
      stateLoader = StateLoader.default(Option.empty[Int]),
      rootPath = PathAndQuery.Root,
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
