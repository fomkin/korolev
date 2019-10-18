import ViewState.{Article, Comment}
import korolev.Context

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

final class BlogView(val ctx: Context.Scope[Future, ViewState, ViewState.Tab.Blog, Any]) {

  import ctx._

  import levsha.dsl._
  import html._

  private val nameInput: ctx.ElementId = elementId()

  private val commentInput: ctx.ElementId = elementId()

  def onSendComment(article: Article)(access: Access): Future[Unit] = {
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

  def onAddComment(article: Article)(access: Access): Future[Unit] = {
    access.transition(_.copy(addCommentFor = Some(article.id)))
  }

  def apply(state: ViewState.Tab.Blog): Node = optimize {
    div(
      width @= "500px",
      state.articles map { article =>
        div(
          p(article.text),
          div(marginTop @= "20px", marginLeft @= "20px",
            article.comments map { comment =>
              div(
                div(fontWeight @= "bold", s"${comment.author}:"),
                p(comment.text)
              )
            },
            state.addCommentFor match {
              case Some(article.id) =>
                form(
                  input(nameInput, `type` := "text", placeholder := "Name"),
                  input(commentInput, `type` := "text", placeholder := "Comment"),
                  button("Send comment"),
                  event("submit")(onSendComment(article))
                )
              case _ =>
                button(
                  "Add comment",
                  event("click")(onAddComment(article))
                )
            }
          )
        )
      }
    )
  }
}
