case class ViewState(blogName: String, tab: ViewState.Tab)

object ViewState {

  case class Comment(text: String, author: String)
  case class Article(id: Int, text: String, comments: List[Comment])

  sealed trait Tab

  object Tab {
    case class Blog(articles: List[Article], addCommentFor: Option[Int] = None) extends Tab
    object Blog {
      val default: Blog = {
        Blog(
          List(
            Article(0,
              """In the beginning, the creators of the Web designed browser
                |as a thin client for web servers. The browser displayed
                |hypertext pages received from a server. It was simple and
                |elegant. As is often the case, a beautiful idea confronted
                |reality, and after a few years, browser manufacturers added
                |support for a scripting language. At first, it was intended
                |only for decorating. Until the middle of the first decade,
                |it was considered proper to create websites with JS as just
                |as an option.""".stripMargin,
              List(
                Comment("Yoy are beauty guy! Author, write more!", "Oleg"),
                Comment("Blog post is shit, author is an asshole", "Vitalij")
              )
            ),
            Article(1,
              """The modern approach of website development is the result
                |of the increasing requirements to user interface interactivity.
                |Tasks to improve interactivity fell on the shoulders of
                |template designers. Often they do not have the competence
                |and authority to develop a "cross-cutting" solution.
                |Template designers have learned JS and became front-end
                |engineers. The logic gradually began to flow from the
                |server to the client. It's convenient for the frontend-guy
                |to write everything on the client side. For the backend-guy,
                |it's convenient not to think about the user. "I'll give
                |you JSON, and then I don't care" -- they say. Two years
                |ago, serverless architecture became popular. The suggestion
                |was that the JS applications would work directly with the
                |database and message queues.""".stripMargin,
              List(
                Comment("Hello, bear", "Oleg"),
                Comment("I didn't read but strongly disagree.", "Vitalij")
              )
            )
          )
        )
      }
    }
    case class About(text: String) extends Tab
    object About {
      val default = About("I'm a cow")
    }
  }
}
