package korolev.akkahttp.util

import java.util.concurrent.ConcurrentLinkedQueue

import akka.NotUsed
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.Source
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{Attributes, Outlet, SourceShape}

class WSSubscriptionStage(subscribe: (String => Unit) => Unit) extends GraphStage[SourceShape[Message]] {

  override val shape: SourceShape[Message] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {

      var pulled = false
      val messages = new ConcurrentLinkedQueue[String]

      subscribe { message =>
        messages.add(message)

        if (pulled) {
          pushMessage()
          pulled = false
        }
      }

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          val messageOpt = pushMessage()

          if (messageOpt.isEmpty) {
            pulled = true
          }
        }
      })

      private def pushMessage(): Option[String] = {
        val messageOpt = Option(messages.poll())

        messageOpt.foreach { message =>
          push(out, TextMessage(message))
        }

        messageOpt
      }

    }

  private lazy val out: Outlet[Message] = Outlet("out")

}

object WSSubscriptionStage {

  def source(subscribe: (String => Unit) => Unit): Source[Message, NotUsed] =
    Source.fromGraph(new WSSubscriptionStage(subscribe))

}
