
import zio.{RIO, Runtime, ZIO, ZIOAppDefault, ExitCode as ZExitCode}
import korolev.Context
import korolev.server.{KorolevServiceConfig, StateLoader}
import korolev.web.PathAndQuery
import korolev.zio.Zio2Effect
import korolev.state.javaSerialization.*
import korolev.zio.http.ZioHttpKorolev
import zio.http.HttpApp
import zio.http.Response
import zio.http.Server
import zio.http.Status

import scala.concurrent.ExecutionContext


object ZioHttpExample extends ZIOAppDefault {

  type AppTask[A] = RIO[Any, A]

  private class Service()(implicit runtime: Runtime[Any])  {

    import levsha.dsl._
    import levsha.dsl.html._
    import scala.concurrent.duration._

    implicit val ec: ExecutionContext = Runtime.defaultExecutor.asExecutionContext
    implicit val effect: Zio2Effect[Any, Throwable] = new Zio2Effect[Any, Throwable](runtime, identity, identity)

    val ctx = Context[ZIO[Any, Throwable, *], Option[Int], Any]

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

    def route(): HttpApp[Any, Throwable] = {
      new ZioHttpKorolev[Any].service(config)
    }

  }

  private def getAppRoute(): ZIO[Any, Nothing, HttpApp[Any, Throwable]] = {
    ZIO.runtime[Any].map { implicit rts =>
      new Service().route()
    }
  }


  override def run =
    for {
      httpApp <- getAppRoute()
      _ <- Server
        .serve(httpApp.catchAllZIO(_ => ZIO.succeed(Response.status(Status.InternalServerError))))
        .provide(Server.defaultWithPort(8088))
        .orDie
    } yield ZExitCode.success

}
