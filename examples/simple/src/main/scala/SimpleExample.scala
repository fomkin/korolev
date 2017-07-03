import korolev._
import korolev.server._
import korolev.blazeServer._
import korolev.execution._

import scala.concurrent.Future

object SimpleExample extends KorolevBlazeServer {

  import State.applicationContext._
  import symbolDsl._

  // Handler to input
  val inputId = elementId
  val editInputId = elementId

  val service = blazeService[Future, State, Any] from KorolevServiceConfig [Future, State, Any] (
    serverRouter = ServerRouter.empty[Future, State],
    stateStorage = StateStorage.default(State()),
    render = {
      case state =>
        'body(
          'div("Super TODO tracker"),
          'div('style /= "height: 250px; overflow-y: scroll",
            (state.todos zipWithIndex) map {
              case (todo, i) =>
                'div(
                  'input(
                    'type /= "checkbox",
                    if (state.edit.nonEmpty) 'disabled /= "" else void,
                    if (todo.done) 'checked /= "" else void,
                    // Generate transition when clicking checkboxes
                    event('click) {
                      immediateTransition { case tState =>
                        val updated = tState.todos.updated(i, tState.todos(i).copy(done = !todo.done))
                        tState.copy(todos = updated)
                      }
                    }
                  ),
                  if (state.edit.contains(i)) {
                    'form(
                      'style /= "display: inline-block; margin-bottom: -10px",
                      'input(
                        editInputId,
                        'style /= "display: inline-block",
                        'type /= "text",
                        'value := todo.text
                      ),
                      'button('style /= "display: inline-block", "Save"),
                      eventWithAccess('submit) { access =>
                        deferredTransition {
                          access.property[String](editInputId, 'value) map { value =>
                            transition { case s =>
                              val updatedTodo = s.todos(i).copy(text = value)
                              val updatedTodos = s.todos.updated(i, updatedTodo)
                              s.copy(todos = updatedTodos, edit = None)
                            }
                          }
                        }
                      }
                    )
                  } else {
                    'span(
                      if (todo.done) 'style /= "text-decoration: line-through" else void,
                      todo.text,
                      event('dblclick) {
                        immediateTransition {
                          case s => s.copy(edit = Some(i))
                        }
                      }
                    )
                  }
                )
            }
          ),
          'form(
            // Generate AddTodo action when 'Add' button clicked
            eventWithAccess('submit) { access =>
              val prop = access.property[String](inputId)
              deferredTransition {
                prop.get('value) flatMap { value =>
                  prop.set('value, "") map { _ =>
                    val todo = State.Todo(value, done = false)
                    transition { case tState =>
                      tState.copy(todos = tState.todos :+ todo)
                    }
                  }
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

case class State(
  todos: Vector[State.Todo] = (0 to 20).toVector.map(i => State.Todo(s"This is TODO #$i", done = false)),
  edit: Option[Int] = None
)

object State {
  val applicationContext = ApplicationContext[Future, State, Any]
  case class Todo(text: String, done: Boolean)
}

