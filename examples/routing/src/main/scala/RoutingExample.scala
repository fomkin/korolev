import korolev._
import korolev.server._
import korolev.blazeServer._
import korolev.execution._
import korolev.state.javaSerialization._

import scala.concurrent.Future

object RoutingExample extends KorolevBlazeServer {

  import State.globalContext._
  import symbolDsl._

  val storage = StateStorage.default[Future, State](State())
  val inputId = elementId()

  val service = blazeService[Future, State, Any] from KorolevServiceConfig [Future, State, Any] (
    stateStorage = storage,
    head = {
      Seq(
        'title("Main Routing Page"),
        'link(
          'href /= "/main.css",
          'rel /= "stylesheet",
          'type /= "text/css"
        )
      )
    },
    render = {
      case state =>
        'body (
          'div ("Super TODO tracker"),
          'div (
            state.todos.keys map { name =>
              'a(
                event('click) { access =>
                  access.transition(_.copy(selectedTab = name))
                },
                'href /= "/" + name.toLowerCase, disableHref,
                'marginLeft @= 10,
                if (name == state.selectedTab) 'strong (name)
                else name
              )
            }
          ),
          'div ('class /= "todos",
            (state.todos(state.selectedTab) zipWithIndex) map {
              case (todo, i) =>
                'div(
                  'div(
                    'class /= {
                      if (!todo.done) "checkbox"
                      else "checkbox checkbox__checked"
                    },
                    // Generate transition when clicking checkboxes
                    event('click) { access =>
                      access.transition { s =>
                        val todos = s.todos(s.selectedTab)
                        val updated = todos.updated(i, todos(i).copy(done = !todo.done))
                        s.copy(todos = s.todos + (s.selectedTab -> updated))
                      }
                    }
                  ),
                  if (!todo.done) 'span(todo.text)
                  else 'strike(todo.text)
                )
            }
          ),
          'form (
            // Generate AddTodo action when 'Add' button clicked
            event('submit) { access =>
              access.property(inputId, 'value) flatMap { value =>
                val todo = State.Todo(value, done = false)
                access.transition { s =>
                  s.copy(todos = s.todos + (s.selectedTab -> (s.todos(s.selectedTab) :+ todo)))
                }
              }
            },
            'input (
              inputId,
              'type /= "text",
              'placeholder /= "What should be done?"
            ),
            'button ("Add todo")
          )
        )
    },
    router = { (deviceId, _) =>
      Router(
        fromState = {
          case State(tab, _) =>
            Root / tab.toLowerCase
        },
        toState = {
          case (Some(s), Root) =>
            val u = s.copy(selectedTab = s.todos.keys.head)
            Future.successful(u)
          case (Some(s), Root / name) =>
            val key = s.todos.keys.find(_.toLowerCase == name)
            Future.successful(key.fold(s)(k => s.copy(selectedTab = k)))
          case (None, Root) =>
            storage.createTopLevelState(deviceId)
          case (None, Root / name) =>
            storage.createTopLevelState(deviceId) map { s =>
              val key = s.todos.keys.find(_.toLowerCase == name)
              key.fold(s)(k => s.copy(selectedTab = k))
            }
        }
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
  val globalContext = Context[Future, State, Any]
  case class Todo(text: String, done: Boolean)
  object Todo {
    def apply(n: Int): Vector[Todo] = (0 to n).toVector map {
      i => Todo(s"This is TODO #$i", done = false)
    }
  }
}

