import korolev._
import korolev.server._
import korolev.akkahttp._
import korolev.execution._
import korolev.state.javaSerialization._

import scala.concurrent.Future

object SimpleExample extends SimpleAkkaHttpKorolevApp {

  import State.globalContext._
  import symbolDsl._

  // Handler to input
  val inputId = elementId()
  val editInputId = elementId()

  val service = akkaHttpService {
    KorolevServiceConfig [Future, State, Any] (
      router = Router.empty,
      stateStorage = StateStorage.default(State()),
      render = {
        case state =>
          'body(
            'div("Super TODO tracker"),
            'div('height @= 250, 'overflowY @= "scroll",
              (state.todos zipWithIndex) map {
                case (todo, i) =>
                  'div(
                    'input(
                      'type /= "checkbox",
                      if (state.edit.nonEmpty) 'disabled /= "" else void,
                      if (todo.done) 'checked /= "" else void,
                      // Generate transition when clicking checkboxes
                      event("click") { access =>
                        access.transition { s =>
                          val updated = s.todos.updated(i, s.todos(i).copy(done = !todo.done))
                          s.copy(todos = updated)
                        }
                      }
                    ),
                    if (state.edit.contains(i)) {
                      'form(
                        'marginBottom @= -10,
                        'display @= "inline-block",
                        'input(
                          editInputId,
                          'display @= "inline-block",
                          'type /= "text",
                          'value := todo.text
                        ),
                        'button('display @= "inline-block", "Save"),
                        event("submit") { access =>
                          access.property(editInputId, 'value) flatMap { value =>
                            access.transition { s =>
                              val updatedTodo = s.todos(i).copy(text = value)
                              val updatedTodos = s.todos.updated(i, updatedTodo)
                              s.copy(todos = updatedTodos, edit = None)
                            }
                          }
                        }
                      )
                    } else {
                      'span(
                        if (todo.done) 'textDecoration @= "line-through" else void,
                        todo.text,
                        event("dblclick") { access =>
                          access.transition(_.copy(edit = Some(i)))
                        }
                      )
                    }
                  )
              }
            ),
            'form(
              // Generate AddTodo action when 'Add' button clicked
              event("submit") { access =>
                val prop = access.property(inputId)
                prop.get('value) flatMap { value =>
                  prop.set('value, "") flatMap { _ =>
                    val todo = State.Todo(value, done = false)
                    access.transition(s => s.copy(todos = s.todos :+ todo))
                  }
                }
              },
              'input(
                if (state.edit.nonEmpty) 'disabled /= "" else void,
                inputId,
                'type /= "text",
                'placeholder /= "What should be done?"
              ),
              'button(
                if (state.edit.nonEmpty) 'disabled /= "" else void,
                "Add todo"
              )
            )
          )
      }
    )
  }
}

case class State(
  todos: Vector[State.Todo] = (0 to 9).toVector.map(i => State.Todo(s"This is TODO #$i", done = false)),
  edit: Option[Int] = None
)

object State {
  val globalContext = Context[Future, State, Any]
  case class Todo(text: String, done: Boolean)
}

