import korolev._
import korolev.akkahttp._
import korolev.execution._
import korolev.server._
import korolev.state.javaSerialization._

import scala.concurrent.Future

object RoutingExample extends SimpleAkkaHttpKorolevApp {

  import State.globalContext._

  import levsha.dsl._
  import html._

  val storage = StateStorage.default[Future, State](State())
  val inputId = elementId()

  val service = akkaHttpService{
    KorolevServiceConfig [Future, State, Any] (
      stateStorage = storage,
      head = _ => {
        Seq(
          title("Main Routing Page"),
          link(
            href := "/static/main.css",
            rel := "stylesheet",
            `type` := "text/css"
          )
        )
      },
      render = {
        case state => optimize {
          body(
            div("Super TODO tracker"),
            div(
              state.todos.keys map { name =>
                a(
                  event("click") { access =>
                    access.transition(_.copy(selectedTab = name))
                  },
                  href := "/" + name.toLowerCase,
                  preventDefaultClickBehavior,
                  marginLeft @= "10px",
                  if (name == state.selectedTab) strong(name)
                  else name
                )
              }
            ),
            div(clazz := "todos",
              (state.todos(state.selectedTab) zipWithIndex) map {
                case (todo, i) =>
                  div(
                    div(
                      clazz := {
                        if (!todo.done) "checkbox"
                        else "checkbox checkbox__checked"
                      },
                      // Generate transition when clicking checkboxes
                      event("click") { access =>
                        access.transition { s =>
                          val todos = s.todos(s.selectedTab)
                          val updated = todos.updated(i, todos(i).copy(done = !todo.done))
                          s.copy(todos = s.todos + (s.selectedTab -> updated))
                        }
                      }
                    ),
                    if (!todo.done) span(todo.text)
                    else span(textDecoration @= "line-through", todo.text)
                  )
              }
            ),
            form(
              // Generate AddTodo action when 'Add' button clicked
              event("submit") { access =>
                access.valueOf(inputId) flatMap { value =>
                  val todo = State.Todo(value, done = false)
                  access.transition { s =>
                    s.copy(todos = s.todos + (s.selectedTab -> (s.todos(s.selectedTab) :+ todo)))
                  }
                }
              },
              input(
                inputId,
                `type` := "text",
                placeholder := "What should be done?"
              ),
              button("Add todo")
            )
          )
        }
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
    .zipWithIndex
    .map { case (tab, i) => tab -> State.Todo(i) }
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

