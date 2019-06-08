import korolev._
import korolev.akkahttp._
import korolev.execution._
import korolev.server._
import korolev.state.javaSerialization._

import scala.concurrent.Future
import scala.util.Random

object RoutingExample extends SimpleAkkaHttpKorolevApp {

  import State.globalContext._
  import symbolDsl._

  val storage = StateStorage.default[Future, State](State())
  val inputId = elementId()

  val service = akkaHttpService{
    KorolevServiceConfig [Future, State, Any] (
      stateStorage = storage,
      head = {
        Seq(
          'title("Main Routing Page"),
          'link(
            'href /= "/static/main.css",
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
      router = Router(
        fromState = {
          case State(tab, _) =>
            Root / tab.toLowerCase
        },
        toState = {
          case Root => initialState =>
            Future.successful(initialState)
          case Root / name if State.Tabs.exists(_.toLowerCase == name.toLowerCase) => initialState =>
            val key = initialState.todos.keys.find(_.toLowerCase == name)
            Future.successful(key.fold(initialState)(k => initialState.copy(selectedTab = k)))
        }
      )
    )
  }
}

case class State(
  selectedTab: String = "Tab1",
  todos: Map[String, Vector[State.Todo]] = State.Tabs
    .map(tab => tab -> State.Todo(Random.nextInt(7)))
    .toMap
)

object State {

  final val Tabs = Seq("Tab1", "Tab2", "Tab3")

  val globalContext = Context[Future, State, Any]
  case class Todo(text: String, done: Boolean)
  object Todo {
    def apply(n: Int): Vector[Todo] = (0 to n).toVector map {
      i => Todo(s"This is TODO #$i", done = false)
    }
  }
}

