import korolev.Korolev.EventFactory
import korolev.{Event, KorolevServer, Shtml}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
object Example extends App with Shtml {

  sealed trait Action

  object Action {
    case class AddTodo(todo: Todo) extends Action
    case class TodoSetDone(i: Int, done: Boolean) extends Action
  }

  case class Todo(text: String, done: Boolean)

  case class State(todos: Vector[Todo] = (0 to 10).toVector map {
    i => Todo(s"This is TODO #$i", done = false)
  })

  KorolevServer[State, Action](
    initialState = State(),
    reducer = {
      case (state, Action.AddTodo(todo)) =>
        state.copy(todos = state.todos :+ todo)
      case (state, Action.TodoSetDone(i, done)) =>
        val updated = state.todos.updated(i, state.todos(i).copy(done = done))
        state.copy(todos = updated)
    },
    initRender = { access =>

      // Handler to input
      val inputId = access.id(())

      // Generate actions when clicking checkboxes
      val todoClick: EventFactory[(Int, Todo)] =
        access.event("click", Event.AtTarget) { case (i, todo) =>
          val res = Action.TodoSetDone(i, done = !todo.done)
          true -> Future.successful(res)
        }

      // Generate AddTodo action when 'Add' button clicked
      val addTodoClick: EventFactory[Unit] =
        access.event("click") { _ =>
          val future = inputId[String]('value) map { value =>
            Action.AddTodo(Todo(value, done = false))
          }
          true -> future
        }

      // Create a DOM using state
      state => {
        'div(
          'div("Super TODO tracker"),
          'div('style /= "height: 250px; overflow-y: scroll",
            (state.todos zipWithIndex) map {
              case (todo, i) =>
                'div(
                  'input(
                    'type /= "checkbox",
                    'checked := todo.done,
                    todoClick(i, todo)
                  ),
                  if (!todo.done) 'span(todo.text)
                  else 'strike(todo.text)
                )
            }
          ),
          'div(
            'input(
              inputId,
              'type /= "text",
              'placeholder /= "What should be done?"
            ),
            'button(
              "Add todo",
              addTodoClick(())
            )
          )
        )
      }
    }
  )
}
