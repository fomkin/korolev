package bridge

sealed trait Hook {
  val requestString: String
}

/**
 * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
 */
object Hook {

  case object Success extends Hook {
    val requestString = "@hook_success"
  }

  case object Failure extends Hook {
    val requestString = "@hook_failure"
  }

}
