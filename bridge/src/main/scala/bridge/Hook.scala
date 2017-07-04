package bridge

sealed trait Hook {
  val requestString: String
}

object Hook {

  case object Success extends Hook {
    val requestString = "@hook_success"
  }

  case object Failure extends Hook {
    val requestString = "@hook_failure"
  }

}
