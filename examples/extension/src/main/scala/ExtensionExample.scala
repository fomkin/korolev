import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Sink, Source}
import korolev._
import korolev.akka._
import korolev.server._
import korolev.state.javaSerialization._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ExtensionExample extends SimpleAkkaHttpKorolevApp {

  private val ctx = Context[Future, List[String], String]

  import ctx._

  private val (queue, queueSource) = Source
    .queue[String](10, OverflowStrategy.fail)
    .preMaterialize()

  private val topicListener = Extension.pure[Future, List[String], String] { access =>
    val queueSink = queueSource.runWith(Sink.queue[String])
    def aux(): Future[Unit] = queueSink.pull() flatMap {
      case Some(message) => access
        .transition(_ :+ message)
        .flatMap(_ => aux())
      case None =>
        Future.unit
    }
    aux()
    Extension.Handlers[Future, List[String], String](
      onMessage = message => queue.offer(message).map(_ => ()),
      onDestroy = () => Future.successful(queueSink.cancel())
    )
  }

  private def onSubmit(access: Access) = {
    for {
      sessionId <- access.sessionId
      name <- access.valueOf(nameElement)
      text <- access.valueOf(textElement)
      userName =
        if (name.isBlank) s"Anonymous #${sessionId.hashCode().toHexString}"
        else name
      _ <-
        if (text.isBlank) Future.unit
        else access.publish(s"$userName: $text")
      _ <- access.property(textElement).set("value", "")
    } yield ()
  }

  private val nameElement = elementId()
  private val textElement = elementId()

  private val config = KorolevServiceConfig[Future, List[String], String](
    stateLoader = StateLoader.default(Nil),
    extensions = List(topicListener),
    render = { message =>

      import levsha.dsl._
      import html._

      optimize {
        body(
          div(
            backgroundColor @= "yellow",
            padding @= "10px",
            border @= "1px solid black",
            "This is a chat. Open this app in few browser tabs or on few different computers"
          ),
          div(
            marginTop @= "10px",
            padding @= "10px",
            height @= "250px",
            backgroundColor @= "#eeeeee",
            message map { x =>
              div(x)
            }
          ),
          form(
            marginTop @= "10px",
            input(`type` := "text", placeholder := "Name", nameElement),
            input(`type` := "text", placeholder := "Message", textElement),
            button("Sent"),
            event("submit")(onSubmit)
          )
        )
      }
    }
  )

  val service: AkkaHttpService =
    akkaHttpService(config)
}
