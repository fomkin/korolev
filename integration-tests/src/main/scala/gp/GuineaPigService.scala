package gp

import korolev._
import korolev.server._
import korolev.execution._
import korolev.state.javaSerialization._

import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration._

import pushka.json._
import pushka.Ast

object GuineaPigService {

  case class State(
    selectedTab: String = "tab1",
    todos: Map[String, Vector[State.Todo]] = Map(
      "tab1" -> State.Todo(5),
      "tab2" -> State.Todo(7),
      "tab3" -> State.Todo(2)
    ),
    uploadedText: String = "",
    delayOn: Boolean = false,
    eventFromComponentReceived: Boolean = false,
    key: Option[String] = None
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

  import State.globalContext._
  import State.globalContext.symbolDsl._

  val logger = LoggerFactory.getLogger("GuineaPig")
  val storage = StateStorage.default[Future, State](State())

  val uploadFormId = elementId()
  val inputId = elementId()

  val TheComponent = Component[Future, Int, String, Unit](0) { (context, label, state) =>
    import context._
    import symbolDsl._
    'span(
      'id /= "the-component",
      s"$label $state",
      event('click) { access =>
        val future =
          if (state == 5) access.publish(())
          else Future.successful(())
        future.flatMap { _ =>
          access.transition {
            case n => n + 1
          }
        }
      }
    )
  }

  val service = KorolevServiceConfig[Future, State, Any](
    stateStorage = storage,
    head = {
      Seq(
        'title("The Test App"),
        'link('href /= "/main.css", 'rel /= "stylesheet", 'type /= "text/css"),
        'meta('content/="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0", 'name /= "viewport"),
        'script('src /= "/debug-console.js")
      )
    },
    render = {
      case state =>
        'body(
          'div("Super TODO tracker"),
          'div(
            state.todos.keys map { name =>
              'span(
                'id /= name,
                event('click) { access =>
                  access.transition { case s =>
                    s.copy(selectedTab = name)
                  }
                },
                'marginLeft @= 10,
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
                  'class /= {
                    if (todo.done) "todo todo__finished"
                    else "todo"
                  },
                  'div(
                    'class /= {
                      if (!todo.done) "todo_checkbox"
                      else "todo_checkbox todo_checkbox__checked"
                    },
                    // Generate transition when clicking checkboxes
                    event('click) { access =>
                      access.transition { case s =>
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
            event('submit) { access =>
              logger.info("Submit clicked")
              val property = access.property(inputId)
              property.get('value) flatMap { value =>
                logger.info("Value received")
                property.set('value, "property value") flatMap { _ =>
                  val todo = State.Todo(value, done = false)
                  access.transition { case s =>
                    s.copy(todos = s.todos + (s.selectedTab -> (s.todos(s.selectedTab) :+ todo)))
                  }
                }
              }
            },
            'input(
              inputId,
              'id /= "todo-input",
              'type /= "text",
              'placeholder /= "What should be done?",
              event('keydown) { access =>
                access.eventData.flatMap { jsonString =>
                  val data = read[Map[String, Ast]](jsonString)
                  data.get("key")
                    .collect { case Ast.Str(s) => s }
                    .fold(Future.successful(())) { key =>
                      access.transition(_.copy(key = Some(key)))
                    }
                }
              }
            ),
            'button(
              'id /= "todo-submit-button",
              "Add todo"
            )
          ),
          'div(
            state.uploadedText match {
              case "" => void
              case s => 'div('id /= "upload-text", s)
            },
            'form(
              uploadFormId,
              'id /= "upload-form",
              'input('type /= "file", 'name /= "upload-input"),
              'button('id /= "upload-button", "Submit"),
              event('submit) { access =>
                access
                  .downloadFormData(uploadFormId)
                  .start()
                  .flatMap { result =>
                    access.transition {
                      case s =>
                        s.copy(uploadedText = result.text("upload-input"))
                    }
                  }
              }
            )
          ),
          'div('id /= "delay-text",
            if (state.delayOn) "Wait a second" else "Click me",
            if (state.delayOn) {
              delay(2.second) { access =>
                access.transition {
                  case s => s.copy(delayOn = false)
                }
              }
            } else {
              event('click) { access =>
                access.transition {
                  case s => s.copy(delayOn = true)
                }
              }
            }
          ),
          'span('id /= "theKey", state.key.fold("<no key>")(identity)),
          'div(
            TheComponent("label") { (access, _) =>
              access.transition(_.copy(eventFromComponentReceived = true))
            },
            'span('id /= "from-component",
              if (state.eventFromComponentReceived) "Cat"
              else "Dog"
            )
          )
        )
    },
    router = { (deviceId, _) =>
      Router(
        fromState = {
          case State(tab, _, _, _, _, _) =>
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


