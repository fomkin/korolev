import korolev.Context
import korolev.akka._
import korolev.server.{KorolevServiceConfig, StateLoader}
import korolev.state.javaSerialization._
import korolev.monix._

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global

object MonixExample extends SimpleAkkaHttpKorolevApp {

  val ctx = Context[Task, Option[String], Any]

  import ctx._

  private val aInput = elementId()
  private val bInput = elementId()

  def service: AkkaHttpService = akkaHttpService {
    KorolevServiceConfig[Task, Option[String], Any](
      stateLoader = StateLoader.default(None),
      render = { maybeResult =>
        import levsha.dsl._
        import html._
        optimize {
          body(
            form(
              input(aInput, `type` := "number", event("input")(onChange)),
              span("+"),
              input(bInput, `type` := "number", event("input")(onChange)),
              span("="),
              maybeResult.map(result => span(result)),
            )
          )
        }
      }
    )
  }

  private def onChange(access: Access) =
    for {
      a <- access.valueOf(aInput)
      b <- access.valueOf(bInput)
      _ <-
        if (a.trim.isEmpty || b.trim.isEmpty) Task.unit
        else access.transition(_ => Some((a.toInt + b.toInt).toString))
    } yield ()
}