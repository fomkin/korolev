case class ViewState(blogName: String, tab: ViewState.Tab)

object ViewState {

  case class Comment(text: String, author: String)
  case class Article(id: Int, text: String, comments: List[Comment])

  sealed trait Tab

  object Tab {
    case class Blog(articles: List[Article], addCommentFor: Option[Int] = None) extends Tab
    object Blog {
      val default = Blog(List(
        Article(0, "Scala is awesome!", List(Comment("Agree", "Oleg"), Comment("Disagree", "Vitalij"))),
        Article(1, "Haskell is awesome!", List(Comment("Agree", "Oleg"), Comment("Agree", "Vitalij"))),
      ))
    }
    case class About(text: String) extends Tab
    object About {
      val default = About("I'm a cow")
    }
  }
}
