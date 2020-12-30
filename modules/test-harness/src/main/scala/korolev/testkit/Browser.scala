package korolev.testkit

import korolev.{Context, Qsid, Transition}
import korolev.Context.{Access, Binding, ComponentEntry, ElementId}
import korolev.effect.Effect
import korolev.effect.syntax._
import korolev.effect.io.LazyBytes
import korolev.internal.Frontend.ClientSideException
import korolev.util.JsCode
import korolev.web.FormData
import levsha.Document.Node
import levsha.events.EventId
import levsha.{IdBuilder, RenderContext, XmlNs}
import org.graalvm.polyglot.{HostAccess, Value}

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.NoSuchElementException
import scala.collection.mutable

case class Browser(properties: Map[(ElementId, String), String] = Map.empty,
                   forms: Map[ElementId, FormData] = Map.empty,
                   filesMap: Map[ElementId, Map[String, Array[Byte]]] = Map.empty,
                   jsMocks: List[String] = Nil) {

  def property(id: ElementId, name: String, value: String): Browser =
    copy(properties = properties + ((id -> name, value)))

  def value(id: ElementId, value: String): Browser =
    property(id, "value", value)

  def form(id: ElementId, data: FormData): Browser =
    copy(forms = forms + (id -> data))

  def mockJs(script: String): Browser =
    copy(jsMocks = script :: jsMocks)

  def form(id: ElementId, fields: (String, String)*): Browser = {
    val entries = fields map {
      case (k, v) =>
        val data = ByteBuffer.wrap(v.getBytes(StandardCharsets.UTF_8))
        FormData.Entry(k, data, Nil)
    }
    copy(forms = forms + ((id, FormData(entries))))
  }

  /**
   * Add `file` to `id`.
   */
  def file(id: ElementId, file: (String, Array[Byte])): Browser =
    filesMap.get(id) match {
      case None => copy(filesMap = filesMap + (id -> Map(file)))
      case Some(knownFiles) => copy(filesMap = filesMap + (id -> (knownFiles + file)))
    }

  /**
   * Set files at `id` to `filesList`.
   */
  def files(id: ElementId, filesList: (String, Array[Byte])*): Browser =
    copy(filesMap = filesMap + (id -> Map(filesList:_*)))

  /**
   * Simulate event propagation on the given DOM.
   *
   * {{{
   *
   * def onClick(access: Access) = ???
   *
   * val dom = body(
   *   div("Hello world"),
   *   button(
   *     event("click")(onClick),
   *     name := "my-button",
   *     "Click me"
   *   )
   * )
   *
   * val actions = Browser.event(
   *   state = myInitialState,
   *   dom = dom,
   *   event = "click"
   *   target = _.byName("by-button").headOption,
   *  )
   * }}}
   */
  def event[F[_]: Effect, S, M](state: S,
                                dom: levsha.Document.Node[Binding[F, S, M]],
                                event: String,
                                target: PseudoDom => Option[levsha.Id],
                                eventData: String = ""): F[Seq[Action[F, S, M]]] = {

    val rr = Browser.render(dom)
    target(rr.pseudoDom).fold(Effect[F].pure(Seq.empty[Action[F, S, M]])) { target =>
      val propagation = levsha.events.calculateEventPropagation(target, event)

      // (continue propagation, list of batches of actions)
      val (_, actions) = propagation.foldLeft((true, List.empty[F[Seq[Action[F, S, M]]]])) {
        case (continue @ (false, _), _) => continue
        case (continue @ (true, acc), eventId) =>
          rr.events.get(eventId) match {
            case None => continue
            case Some(event) =>
              val actionsF = access(state, event.effect, eventData, rr.elements)
              (!event.stopPropagation, actionsF :: acc)
          }
      }

      Effect[F]
        .sequence(actions)
        .map(_.flatten)
    }
  }

  /**
   * Applies `f` to the Browser using [[Context.Access]].
   */
  def access[F[_]: Effect, S, M](initialState: S,
                                 f: Access[F, S, M] => F[Unit],
                                 eventData: String = "",
                                 elements: Map[levsha.Id, ElementId] = Map.empty): F[Seq[Action[F, S, M]]] = {

    val ed = eventData
    val actions = mutable.Buffer.empty[Action[F, S, M]]
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

      def eventData: F[String] = Effect[F].pure(ed)

      def publish(message: M): F[Unit] = Effect[F].delay(actions += Action.Publish(message))

      def downloadFormData(id: ElementId): F[FormData] =
        Effect[F].delay(browser.forms(id))

      def downloadFiles(id: ElementId): F[List[(Context.FileHandler, Array[Byte])]] =
        Effect[F].delay {
          browser.filesMap(id).toList map {
            case (name, bytes) => Context.FileHandler(name, bytes.length)(id) -> bytes
          }
        }

      def downloadFilesAsStream(id: ElementId): F[List[(Context.FileHandler, LazyBytes[F])]] =
        Effect[F].sequence {
          browser.filesMap(id).toList map {
            case (name, bytes) =>
              LazyBytes(bytes) map { lb =>
                Context.FileHandler(name, bytes.length)(id) -> lb
              }
          }
        }

      def downloadFileAsStream(handler: Context.FileHandler): F[LazyBytes[F]] = {
        Effect[F].delayAsync {
          val bytesOpt = browser.filesMap.collectFirst {
            Function.unlift {
              case (_, files) =>
                files.get(handler.fileName)
            }
          }
          bytesOpt match {
            case None => Effect[F].fail(new NoSuchElementException(handler.fileName))
            case Some(bytes) => LazyBytes(bytes)
          }
        }
      }

      def listFiles(id: ElementId): F[List[Context.FileHandler]] =
        Effect[F].delay {
          browser.filesMap(id).toList map {
            case (name, bytes) => Context.FileHandler(name, bytes.length)(id)
          }
        }

      def evalJs(code: JsCode): F[String] = Effect[F]
        .promise[String] { cb =>

          import org.graalvm.polyglot.{Context => GraalContext}

          // TODO prevent reparsing
          // TODO handle system errors

          val context = GraalContext.create()
          val finalCode = code.mkString(elements.map(_.swap))
          val bindings = context.getBindings("js")

          bindings.putMember("code", finalCode)
          bindings.putMember("handler", new Object {
            @HostAccess.Export
            def result(value: String): Unit = {
              val result = Right(value)
              actions += Action.EvalJs(result)
              cb(result)
            }
            @HostAccess.Export
            def error(value: String): Unit = {
              val result = Left(ClientSideException(value))
              actions += Action.EvalJs(result)
              cb(result)
            }
          })

          context.eval("js",
            jsMocks.mkString("\n") + """
              var result;
              var status = 0;
              try {
                result = eval(code);
              } catch (e) {
                console.error(`Error evaluating code ${code}`, e);
                result = e;
                status = 1;
              }

              if (result instanceof Promise) {
                result.then(
                  (res) => handler.result(JSON.stringify(res))
                  (err) => {
                    console.error(`Error evaluating code ${code}`, err);
                    handler.error(err.toString())
                  }
                );
              } else {
                var resultString;
                if (status === 1) {
                  resultString = result.toString();
                  handler.error(resultString);
                } else {
                  resultString = JSON.stringify(result);
                  handler.result(resultString);
                }
              }
            """
          )
        }

      def registerCallback(name: String)(f: String => F[Unit]): F[Unit] =
        Effect[F].delay {
          actions += Action.RegisterCallback(name, f)
        }
    }

    f(stub).as(actions)
  }

}

object Browser {

  private class PseudoDomRenderContext[F[_], S, M] extends RenderContext[Binding[F, S, M]] {

    val idBuilder = new IdBuilder(256)
    var currentChildren: List[List[PseudoDom]] = List(Nil)
    var currentNode = List.empty[(XmlNs, String)]
    var currentAttrs = List.empty[(XmlNs, String, String)]
    var currentStyles = List.empty[(String, String)]

    var elements = List.empty[(levsha.Id, ElementId)]
    var events = List.empty[(EventId, Context.Event[F, S, M])]

    def openNode(xmlns: XmlNs, name: String): Unit = {
      currentNode = (xmlns, name) :: currentNode
      currentChildren = Nil :: currentChildren
      currentAttrs = Nil
      currentStyles = Nil
      idBuilder.incId()
      idBuilder.incLevel()
    }

    def closeNode(name: String): Unit = {
      idBuilder.decLevel()
      val (xmlns, _) :: currentNodeTail = currentNode
      val children :: currentChildrenTail = currentChildren
      val c2 :: cct2 = currentChildrenTail
      val node = PseudoDom.Element(
        id = idBuilder.mkId,
        ns = xmlns,
        tagName = name,
        attributes = currentAttrs.map(x => (x._2, x._3)).toMap,
        styles = currentStyles.toMap,
        children = children.reverse
      )
      currentNode = currentNodeTail
      currentChildren = (node :: c2) :: cct2
    }

    def setAttr(xmlNs: XmlNs, name: String, value: String): Unit = {
      currentAttrs = (xmlNs, name, value) :: currentAttrs
    }

    def setStyle(name: String, value: String): Unit = {
      currentStyles = (name, value) :: currentStyles
    }

    def addTextNode(text: String): Unit = {
      idBuilder.incId()
      val children :: xs = currentChildren
      val updatedChildren = PseudoDom.Text(idBuilder.mkId, text) :: children
      currentChildren = updatedChildren :: xs
    }

    def addMisc(misc: Binding[F, S, M]): Unit = misc match {
      case ComponentEntry(c, p, _) =>
        val rc = this.asInstanceOf[RenderContext[Context.Binding[F, Any, Any]]]
        c.render(p, c.initialState).apply(rc)
      case elementId: ElementId =>
        elements = (idBuilder.mkId, elementId) :: elements
      case event: Context.Event[F, S, M] =>
        val eventId = EventId(idBuilder.mkId, event.`type`, event.phase)
        events = (eventId, event) :: events
    }
  }

  case class RenderingResult[F[_], S, M](pseudoDom: PseudoDom,
                                         elements: Map[levsha.Id, ElementId],
                                         events: Map[EventId, Context.Event[F, S, M]])

  def render[F[_], S, M](node: Node[Binding[F, S, M]]): RenderingResult[F, S, M] = {

    val rc = new PseudoDomRenderContext[F, S, M]()
    node(rc)
    val (top :: _) :: _ = rc.currentChildren
    RenderingResult(top, rc.elements.toMap, rc.events.toMap)
  }
}
