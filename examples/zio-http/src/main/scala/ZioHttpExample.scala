
import zio.{App, RIO, Runtime, ZEnv, ZIO, ExitCode => ZExitCode}
import korolev.Context
import korolev.server.{KorolevServiceConfig, StateLoader}
import korolev.effect.{Effect => KEffect}
import korolev.zio.{ZioEffect, zioEffectInstance}
import korolev.state.javaSerialization._
import korolev.zio.http.ZioHttpKorolev
import zhttp.http.HttpApp
import zhttp.service.Server

import scala.concurrent.ExecutionContext


object ZioHttpExample extends App {

  type AppTask[A] = RIO[ZEnv, A]

  private class Service()(implicit runtime: Runtime[ZEnv])  {

    import levsha.dsl._
    import html._
    import scala.concurrent.duration._

    implicit val ec: ExecutionContext = runtime.platform.executor.asEC
    implicit val effect= new ZioEffect[ZEnv, Throwable](runtime, identity, identity)

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

    def route(): HttpApp[ZEnv, Throwable] = {
      new ZioHttpKorolev[ZEnv].service(config)
    }

  }

  private def getAppRoute(): ZIO[ZEnv, Nothing, HttpApp[ZEnv, Throwable]] = {
    ZIO.runtime[ZEnv].map { implicit rts =>
      new Service().route()
    }
  }


  override def run(args: List[String]): ZIO[ZEnv, Nothing, ZExitCode] = {

    val prog = for {
      httpApp <- getAppRoute()
      _   <- Server.start(8088, httpApp)
    } yield ZExitCode.success

    prog.orDie
  }

}
