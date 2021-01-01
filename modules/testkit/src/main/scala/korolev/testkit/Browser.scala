package korolev.testkit

import korolev.Context.{Access, Binding, ElementId}
import korolev.effect.Effect
import korolev.effect.io.LazyBytes
import korolev.effect.syntax._
import korolev.internal.Frontend.ClientSideException
import korolev.util.JsCode
import korolev.web.FormData
import korolev.{Context, Qsid, Transition}
import org.graalvm.polyglot.HostAccess

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
   * @example {{{
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
   * @see [[access]]
   */
  def event[F[_]: Effect, S, M](state: S,
                                dom: levsha.Document.Node[Binding[F, S, M]],
                                event: String,
                                target: PseudoHtml => Option[levsha.Id],
                                eventData: String = ""): F[Seq[Action[F, S, M]]] = {

    val rr = PseudoHtml.render(dom)
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
   * @param elements evalJs uses this mapping to search elements on the client side.
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
              actions += Action.PropertySet(id, propName, value.toString)
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

          class Handler {
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
          }

          bindings.putMember("code", finalCode)
          bindings.putMember("handler", new Handler())

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
                  (res) => handler.result(JSON.stringify(res)),
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
          ()
        }

      def registerCallback(name: String)(f: String => F[Unit]): F[Unit] =
        Effect[F].delay {
          actions += Action.RegisterCallback(name, f)
        }
    }

    f(stub).map(_ => actions.toSeq)
  }

}
