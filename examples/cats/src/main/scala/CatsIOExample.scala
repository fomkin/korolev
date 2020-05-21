import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import cats.effect.IO
import korolev.Context
import korolev.akka.{AkkaHttpServerConfig, akkaHttpService}
import korolev.cats.IOEffect
import korolev.server.{KorolevServiceConfig, StateLoader}
import korolev.state.javaSerialization._

import scala.concurrent.ExecutionContext.Implicits.global

object CatsIOExample extends App {

  private implicit val actorSystem: ActorSystem = ActorSystem()

  val applicationContext: Context[IO, State, Any] = {
    Context[IO, State, Any]
  }

  import applicationContext._
  import levsha.dsl._
  import html._

  // Handler to input
  private val inputId = elementId()
  private val editInputId = elementId()

  val config = KorolevServiceConfig[IO, State, Any](
    stateLoader = StateLoader.default(State()),
    render = state => {
      body(
        div("Super TODO tracker"),
        div(height @= "250px", overflow @= "scroll",
          (state.todos zipWithIndex) map {
            case (todo, i) =>
              div(
                input(
                  `type` := "checkbox",
                  if (state.edit.nonEmpty) disabled else void,
                  if (todo.done) checked else void,
                  // Generate transition when clicking checkboxes
                  event("click") { access =>
                    access.transition { s =>
                      val updated = s.todos.updated(i, s.todos(i).copy(done = !todo.done))
                      s.copy(todos = updated)
                    }
                  }
                ),
                if (state.edit.contains(i)) {
                  form(
                    marginBottom @= "-10px",
                    display @= "inline-block",
                    input(
                      editInputId,
                      display @= "inline-block",
                      `type` := "text",
                      value := todo.text
                    ),
                    button(display @= "inline-block", "Save"),
                    event("submit") { access =>
                      access.valueOf(editInputId) flatMap { value =>
                        access.transition { s =>
                          val updatedTodo = s.todos(i).copy(text = value)
                          val updatedTodos = s.todos.updated(i, updatedTodo)
                          s.copy(todos = updatedTodos, edit = None)
                        }
                      }
                    }
                  )
                } else {
                  span(
                    if (todo.done) textDecoration @= "line-through" else void,
                    todo.text,
                    event("dblclick") { access =>
                      access.transition(_.copy(edit = Some(i)))
                    }
                  )
                }
              )
          }
        ),
        form(
          // Generate AddTodo action when Add' button clicked
          event("submit") { access =>
            val prop = access.property(inputId)
            prop.get("value") flatMap { value =>
              prop.set("value", "") flatMap { _ =>
                val todo = State.Todo(value, done = false)
                access.transition(s => s.copy(todos = s.todos :+ todo))
              }
            }
          },
          input(
            if (state.edit.nonEmpty) disabled else void,
            inputId,
            `type` := "text",
            placeholder := "What should be done?"
          ),
          button(
            if (state.edit.nonEmpty) disabled else void,
            "Add todo"
          )
        )
      )
    }
  )

  private val route = akkaHttpService(config).apply(AkkaHttpServerConfig())

  Http().bindAndHandle(route, "0.0.0.0", 8080)
}

case class State(
  todos: Vector[State.Todo] = (0 to 9).toVector.map(i => State.Todo(s"This is TODO #$i", done = false)),
  edit: Option[Int] = None
)

object State {
  case class Todo(text: String, done: Boolean)
}
