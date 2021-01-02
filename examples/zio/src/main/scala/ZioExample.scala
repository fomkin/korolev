import korolev.Context
import korolev.akka._
import korolev.server.{KorolevServiceConfig, StateLoader}
import korolev.state.javaSerialization._
import korolev.zio.taskEffectInstance
import zio.{Runtime, Task, ZIO}

import scala.concurrent.ExecutionContext.Implicits.global

object ZioExample extends SimpleAkkaHttpKorolevApp {

  implicit val runtime = Runtime.default
  implicit val effect = taskEffectInstance(runtime)

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

  def service: AkkaHttpService = akkaHttpService {
    KorolevServiceConfig[Task, Option[Int], Any](
      stateLoader = StateLoader.default(None),
      document = maybeResult => optimize {
        Html(
          body(renderForm(maybeResult))
        )
      }
    )
  }

  def onChange(access: Access) =
    for {
      a <- access.valueOf(aInput)
      b <- access.valueOf(bInput)
      _ <-
        if (a.trim.isEmpty || b.trim.isEmpty) ZIO.unit
        else access.transition(_ => Some(a.toInt + b.toInt))
    } yield ()
}