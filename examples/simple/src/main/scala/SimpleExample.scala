import korolev.server.{ServerRouter, StateStorage}
import korolev.BrowserEffects
import korolev.Shtml._
import korolev.blazeServer.defaultExecutor
import korolev.blazeServer.configureHttpService
import korolev.blazeServer.runServer

import scala.concurrent.Future

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
object SimpleExample extends App {

  import State.effects._
  import korolev.EventResult._

  val service = configureHttpService[Future, State](
    serverRouter = ServerRouter.empty[Future, State],
    stateStorage = StateStorage.default(State()),
    render = {

      // Handler to input
      val inputId = elementId

      // Create a DOM using state
      { case state =>
        'body(
          'div("Super TODO tracker"),
          'div('style /= "height: 250px; overflow-y: scroll",
            (state.todos zipWithIndex) map {
              case (todo, i) =>
                'div(
                  'input(
                    'type /= "checkbox",
                    'checked when todo.done,
                    // Generate transition when clicking checkboxes
                    event('click) {
                      immediateTransition { case tState =>
                        val updated = tState.todos.updated(i, tState.todos(i).copy(done = !todo.done))
                        tState.copy(todos = updated)
                      }
                    }
                  ),
                  if (!todo.done) 'span(todo.text)
                  else 'strike(todo.text)
                )
            }
          ),
          'form(
            // Generate AddTodo action when 'Add' button clicked
            eventWithAccess('submit) { access =>
              deferredTransition {
                access.property[String](inputId, 'value) map { value =>
                  val todo = State.Todo(value, done = false)
                  transition { case tState =>
                    tState.copy(todos = tState.todos :+ todo)
                  }
                }
              }
            },
            'input(
              inputId,
              'type /= "text",
              'placeholder /= "What should be done?"
            ),
            'button("Add todo")
          )
        )
      }
    }
  )

  runServer(service)
}

case class State(todos: Vector[State.Todo] = (0 to 2).toVector map {
  i => State.Todo(s"This is TODO #$i", done = false)
})

object State {
  val effects = BrowserEffects[Future, State]
  case class Todo(text: String, done: Boolean)
}

