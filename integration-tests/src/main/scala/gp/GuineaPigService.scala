package gp

import korolev._
import korolev.server._
import korolev.execution._

import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration._

object GuineaPigService {

  case class State(
    selectedTab: String = "tab1",
    todos: Map[String, Vector[State.Todo]] = Map(
      "tab1" -> State.Todo(5),
      "tab2" -> State.Todo(7),
      "tab3" -> State.Todo(2)
    ),
    uploadedText: String = "",
    delayOn: Boolean = false
  )

  object State {
    val applicationContext = ApplicationContext[Future, State, Any]
    case class Todo(text: String, done: Boolean)
    object Todo {
      def apply(n: Int): Vector[Todo] = (0 to n).toVector map {
        i => Todo(s"This is TODO #$i", done = false)
      }
    }
  }

  import State.applicationContext._
  import State.applicationContext.symbolDsl._

  val logger = LoggerFactory.getLogger("GuineaPig")
  val storage = StateStorage.default[Future, State](State())

  val uploadFormId = elementId
  val inputId = elementId

  val service = KorolevServiceConfig[Future, State, Any](
    stateStorage = storage,
    head = {
      Seq(
        'title("The Test App"),
        'link('href /= "/main.css", 'rel /= "stylesheet", 'type /= "text/css"),
        'meta('content/="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0", 'name /= "viewport"),
        'script('src /= "/debug-console.js")
      )
    },
    render = {
      case state =>
        'body(
          'div("Super TODO tracker"),
          'div(
            state.todos.keys map { name =>
              'span(
                'id /= name,
                event('click) {
                  immediateTransition { case s =>
                    s.copy(selectedTab = name)
                  }
                },
                'style /= "margin-left: 10px",
                if (name == state.selectedTab) 'strong(name)
                else name
              )
            }
          ),
          'div(
            'id /= "todo-list",
            'class /= "todos",
            (state.todos(state.selectedTab) zipWithIndex) map {
              case (todo, i) =>
                'div(
                  'class /= {
                    if (todo.done) "todo todo__finished"
                    else "todo"
                  },
                  'div(
                    'class /= {
                      if (!todo.done) "todo_checkbox"
                      else "todo_checkbox todo_checkbox__checked"
                    },
                    // Generate transition when clicking checkboxes
                    event('click) {
                      immediateTransition { case s =>
                        val todos = s.todos(s.selectedTab)
                        val updated = todos.updated(i, todos(i).copy(done = !todo.done))
                        s.copy(todos = s.todos + (s.selectedTab -> updated))
                      }
                    }
                  ),
                  todo.text
                )
            }
          ),
          'form(
            // Generate AddTodo action when 'Add' button clicked
            eventWithAccess('submit) { access =>
              logger.info("Submit clicked")
              deferredTransition {
                val property = access.property[String](inputId)
                property.get('value) flatMap { value =>
                  logger.info("Value received")
                  property.set('value, "property value") map { _ =>
                    val todo = State.Todo(value, done = false)
                    transition { case s =>
                      s.copy(todos = s.todos + (s.selectedTab -> (s.todos(s.selectedTab) :+ todo)))
                    }
                  }
                }
              }
            },
            'input(
              inputId,
              'id /= "todo-input",
              'type /= "text",
              'placeholder /= "What should be done?"
            ),
            'button(
              'id /= "todo-submit-button",
              "Add todo"
            )
          ),
          'div(
            'div('id /= "upload-text", state.uploadedText),
            'form(
              uploadFormId,
              'id /= "upload-form",
              'input('type /= "file", 'name /= "upload-input"),
              'button('id /= "upload-button", "Submit"),
              eventWithAccess('submit) { access =>
                deferredTransition {
                  access
                    .downloadFormData(uploadFormId)
                    .start()
                    .map { result =>
                      transition {
                        case s =>
                          s.copy(uploadedText = result.text("upload-input"))
                      }
                    }
                }
              }
            )
          ),
          'div('id /= "delay-text",
            if (state.delayOn) "Wait a second" else "Click me",
            if (state.delayOn) {
              delay(1.second) {
                case s => s.copy(delayOn = false)
              }
            } else {
              event('click) {
                immediateTransition {
                  case s => s.copy(delayOn = true)
                }
              }
            }
          )
        )
    },
    serverRouter = {
      ServerRouter(
        dynamic = (_, _) => Router(
          fromState = {
            case State(tab, _, _, _) =>
              Root / tab.toLowerCase
          },
          toState = {
            case (s, Root) =>
              val u = s.copy(selectedTab = s.todos.keys.head)
              Future.successful(u)
            case (s, Root / name) =>
              val key = s.todos.keys.find(_.toLowerCase == name)
              Future.successful(key.fold(s)(k => s.copy(selectedTab = k)))
          }
        ),
        static = (deviceId) => Router(
          toState = {
            case (_, Root) =>
              storage.initial(deviceId)
            case (_, Root / name) =>
              storage.initial(deviceId) map { s =>
                val key = s.todos.keys.find(_.toLowerCase == name)
                key.fold(s)(k => s.copy(selectedTab = k))
              }
          }
        )
      )
    }
  )
}
