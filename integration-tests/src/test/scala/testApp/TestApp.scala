package testApp

import korolev._
import korolev.server._
import korolev.blazeServer._

import scala.concurrent.Future

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
object TestApp {

  import State.effects._

  val storage = StateStorage.default[Future, State](State())
  val inputId = elementId

  val service = blazeService[Future, State, Any] from KorolevServiceConfig[Future, State, Any](
    stateStorage = storage,
    head = 'head(
      'title("The Test App"),
      'link(
        'href /= "/main.css",
        'rel /= "stylesheet",
        'type /= "text/css"
      )
    ),
    render = {
      case state =>
        'body(
          'div("Super TODO tracker"),
          'div(
            state.todos.keys map { name =>
              'span(
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
                  'name /= "todo-list-item",
                  'class /= {
                    if (todo.done) "todo__finished"
                    else ""
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
              deferredTransition {
                access.property[String](inputId, 'value) map { value =>
                  val todo = State.Todo(value, done = false)
                  transition { case s =>
                    s.copy(todos = s.todos + (s.selectedTab -> (s.todos(s.selectedTab) :+ todo)))
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
          )
        )
    },
    serverRouter = {
      ServerRouter(
        dynamic = (_, _) => Router(
          fromState = {
            case State(tab, _) =>
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

case class State(
  selectedTab: String = "Tab1",
  todos: Map[String, Vector[State.Todo]] = Map(
    "Tab1" -> State.Todo(5),
    "Tab2" -> State.Todo(7),
    "Tab3" -> State.Todo(2)
  )
)

object State {
  val effects = Effects[Future, State, Any]
  case class Todo(text: String, done: Boolean)
  object Todo {
    def apply(n: Int): Vector[Todo] = (0 to n).toVector map {
      i => Todo(s"This is TODO #$i", done = false)
    }
  }
}

