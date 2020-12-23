package korolev.test

import korolev.{Context, Qsid, Transition}
import korolev.Context.{Access, Binding, ElementId}
import korolev.effect.Effect
import korolev.effect.syntax._
import korolev.effect.io.LazyBytes
import korolev.util.JsCode
import korolev.web.FormData
import levsha.Document.Node
import levsha.{RenderContext, XmlNs}

import scala.collection.mutable

case class Browser(properties: Map[(ElementId, String), String] = Map.empty,
                   forms: Map[ElementId, Map[String, String]] = Map.empty) {

  def property(id: ElementId, name: String, value: String): Browser =
    copy(properties = properties + ((id -> name, value)))

  def value(id: ElementId, value: String): Browser =
    property(id, "value", value)

  def form(id: ElementId, fields: (String, String)*): Browser =
    copy(forms = forms + ((id, fields.toMap)))

  // ----

//  def event[F[_]: Effect, S, M](node: levsha.Document.Node[Binding[F, S, M]],
//                                findNode: PseudoDom => levsha.Id,
//                                eventName: String,
//                                eventData: String = ""): F[Seq[Action[S]]] = {
//
//  }

  def access[F[_]: Effect, S, M](initialState: S)(f: Access[F, S, M] => F[Unit]): F[Seq[Action[S]]] = {

    val actions = mutable.Buffer.empty[Action[S]]
    var browser = this
    var currentState = initialState
    val stub = new Access[F, S, M] {

      def property(id: ElementId): Context.PropertyHandler[F] =
        new Context.PropertyHandler[F] {
          def get(propName: String): F[String] =
            Effect[F].pure(browser.properties(id -> propName))
          def set(propName: String, value: Any): F[Unit] =
            Effect[F].delay {
              browser = browser.property(id, propName, value.toString)
              actions += Action.Property(id, propName, value.toString)
            }
        }

      def focus(id: ElementId): F[Unit] =
        Effect[F].delay(actions += Action.Focus(id))

      def resetForm(id: ElementId): F[Unit] =
        Effect[F].delay(actions += Action.ResetForm(id))

      def state: F[S] = Effect[F].delay(currentState)

      def transition(f: Transition[S]): F[Unit] = {
        Effect[F].delay {
          currentState = f(currentState)
          actions += Action.Transition(currentState)
        }
      }

      def syncTransition(f: Transition[S]): F[Unit] =
        transition(f)

      def sessionId: F[Qsid] = Effect[F].pure(Qsid("test-device", "test-session"))

      def eventData: F[String] = ???
      def publish(message: M): F[Unit] = ???
      def downloadFormData(id: ElementId): F[FormData] = ???
      def downloadFiles(id: ElementId): F[List[(Context.FileHandler, Array[Byte])]] = ???
      def downloadFilesAsStream(id: ElementId): F[List[(Context.FileHandler, LazyBytes[F])]] = ???
      def downloadFileAsStream(handler: Context.FileHandler): F[LazyBytes[F]] = ???
      def listFiles(id: ElementId): F[List[Context.FileHandler]] = ???
      def evalJs(code: JsCode): F[String] = ???
      def registerCallback(name: String)(f: String => F[Unit]): F[Unit] = ???
    }

    f(stub).as(actions)
  }

}

object Browser {


//  def render[F[_], S, M](node: Node[Binding[F, S, M]]): PseudoDom = {
//    class PseudoDomRenderContext extends RenderContext[Binding[F, S, M]] {
//      def openNode(xmlns: XmlNs, name: String): Unit = ???
//      def closeNode(name: String): Unit = ???
//      def setAttr(xmlNs: XmlNs, name: String, value: String): Unit = ???
//      def setStyle(name: String, value: String): Unit = ???
//      def addTextNode(text: String): Unit = ???
//      def addMisc(misc: Binding[F, S, M]): Unit = {
//
//      }
//    }
//
//  }
}
