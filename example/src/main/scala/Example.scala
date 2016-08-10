import korolev.{KorolevServer, Shtml}

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
      val inputId = access.id()
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
                    access.event("click") {
                      val res = Action.TodoSetDone(i, done = !todo.done)
                      Future.successful(res)
                    }
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
              access.event("click") {
                inputId[String]('value) map { value =>
                  Action.AddTodo(Todo(value, done = false))
                }
              }
            )
          )
        )
      }
    }
  )
}
