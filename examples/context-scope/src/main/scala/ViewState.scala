case class ViewState(blogName: String, tab: ViewState.Tab)

object ViewState {

  case class Comment(text: String, author: String)
  case class Article(id: Int, text: String, comments: List[Comment])

  sealed trait Tab

  object Tab {
    case class Blog(articles: List[Article], addCommentFor: Option[Int] = None) extends Tab
    object Blog {
      val default = Blog(List(
        // TODO take something abstract
        Article(0, "Most doobie programs are values of type ConnectionIO[A] or Stream[ConnnectionIO, A] that describe computations requiring a database connection. By providing a means of acquiring a JDBC connection we can transform these programs into computations that can actually be executed. The most common way of performing this transformation is via a Transactor. So summarizing, once you have a Transactor[M] you have a way of discharging ConnectionIO and replacing it with some effectful M like IO. In effect this turns a doobie program into a “real” program value that you can integrate with the rest of your application; all doobieness is left behind.", List(Comment("Yoy are beauty guy! Author, write more!", "Oleg"), Comment("Blog post is shit, author is an asshole", "Vitalij"))),
        Article(1, "JDBC provides a bare-bones connection provider via DriverManager.getConnection, which has the advantage of being extremely simple: there is no connection pooling and thus no configuration required. The disadvantage is that it is quite a bit slower than pooling connection managers, and provides no upper bound on the number of concurrent connections. It executes blocking operations on a similar unbounded pool of daemon threads.\n\nHowever, for test and for experimentation as described in this book (and for situations where you really do want to ensure that you get a truly fresh connection right away) the DriverManager is fine. Support in doobie is via DriverManagerTransactor. To construct one you must pass the name of the driver class and a connect URL. Normally you will also pass a user/password (the API provides several variants matching the DriverManager static API).", List(Comment("Preved, medved", "Oleg"), Comment("I didn't read but strongly disagree.", "Vitalij"))),
      ))
    }
    case class About(text: String) extends Tab
    object About {
      val default = About("I'm a cow")
    }
  }
}
