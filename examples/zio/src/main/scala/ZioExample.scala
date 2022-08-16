import korolev.Context
import korolev.effect.Effect
import korolev.server.{KorolevServiceConfig, StateLoader}
import korolev.state.javaSerialization.*
import korolev.zio.taskEffectLayer
import korolev.server.standalone
import korolev.server
import zio.*

import java.net.InetSocketAddress
import scala.concurrent.ExecutionContext.Implicits.global

object ZioExample extends ZIOAppDefault {

  val address = new InetSocketAddress("localhost", 8080)

  val ctx = Context[Task, Option[Int], Any]

  import ctx._

  val aInput = elementId()
  val bInput = elementId()

  import levsha.dsl._
  import html._

  def renderForm(maybeResult: Option[Int]) = optimize {
    form(
      input(
        aInput,
        name := "a-input",
        `type` := "number",
        event("input")(onChange)
      ),
      span("+"),
      input(
        bInput,
        name := "b-input",
        `type` := "number",
        event("input")(onChange)
      ),
      span(s"= ${maybeResult.fold("?")(_.toString)}")
    )
  }

  def onChange(access: Access) =
    for {
      a <- access.valueOf(aInput)
      _ <- ZIO.logInfo(s"a = $a")
      b <- access.valueOf(bInput)
      _ <- ZIO.logInfo(s"b = $b")
      _ <- access.transition(_ => Some(a.toInt + b.toInt)).unless(a.trim.isEmpty || b.trim.isEmpty)
    } yield ()

  final val app = ZIO.service[Effect[Task]].flatMap { implicit taskEffect =>
    val config =
      KorolevServiceConfig[Task, Option[Int], Any](
        stateLoader = StateLoader.default(None),
        document = maybeResult => optimize {
          Html(
            body(renderForm(maybeResult))
          )
        }
      )
    for {
      _ <- ZIO.logInfo(s"Try to start server at $address")
      handler <- standalone.buildServer[Task, Array[Byte]](
        service = server.korolevService(config),
        address = address,
        gracefulShutdown = false
      )
      _ <- ZIO.unit.forkDaemon *> ZIO.logInfo(s"Server started")
      _ <- handler.awaitShutdown()
    } yield ()
  }

  final val run =
    app.provide(taskEffectLayer)
}