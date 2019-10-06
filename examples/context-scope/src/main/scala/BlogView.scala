import ViewState.Comment
import korolev.Context

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

final class BlogView(val ctx: Context.Scope[Future, ViewState, ViewState.Tab.Blog, Any]) {

  import ctx._
  import symbolDsl._

  private val nameInput: ctx.ElementId = elementId()

  private val commentInput: ctx.ElementId = elementId()

  def apply(state: ViewState.Tab.Blog): Node = 'div(
    'width @= "500px",
    state.articles map { article =>
      'div(
        'p(article.text),
        'div('marginTop @= 20, 'marginLeft @= 20,
          article.comments map { comment =>
            'div(
              'div('fontWeight @= "bold", s"${comment.author}:"),
              'p(comment.text)
            )
          },
          state.addCommentFor match {
            case Some(article.id) =>
              'form(
                'input(nameInput, 'type /= "text", 'placeholder /= "Name"),
                'input(commentInput, 'type /= "text", 'placeholder /= "Comment"),
                'button(
                  "Send comment",
                  event("click") { access =>
                    for {
                      name <- access.valueOf(nameInput)
                      text <- access.valueOf(commentInput)
                      comment = Comment(text, name)
                      _ <- access.transition { state =>
                        val xs = state.articles
                        val i = xs.indexWhere(_.id == article.id)
                        val x = xs(i)
                        val upd = xs.updated(i, x.copy(comments = x.comments :+ comment))
                        state.copy(articles = upd)
                      }
                    } yield ()
                  }
                )
              )
            case _ =>
              'button(
                "Add comment",
                event("click") { access =>
                  access.transition(_.copy(addCommentFor = Some(article.id)))
                }
              )
          }
        )
      )
    }
  )

}
