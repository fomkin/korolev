package korolev.effect

import scala.concurrent.{CanAwait, ExecutionContext, Future, TimeoutException}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

sealed trait BrightFuture[+T] extends Future[T] {

  import BrightFuture._

  def transform[S](f: Try[T] => Try[S])(implicit executor: ExecutionContext): Future[S] =
    Map(this, f, executor)

  def transformWith[S](f: Try[T] => Future[S])(implicit executor: ExecutionContext): Future[S] =
    Bind(this, f, executor)
}

object BrightFuture {

  sealed trait LazyLike[T] extends BrightFuture[T] {

    @volatile private var computed: Try[T] = _
    @volatile private var listeners = List.empty[(Try[T] => _, ExecutionContext)]

    def value: Option[Try[T]] = Option(computed)

    protected def compute()(implicit executor: ExecutionContext): Unit

    protected def complete(value: Try[T]): Unit = {
      val xs = this.synchronized {
        val res = listeners
        listeners = Nil
        computed = value
        res
      }
      xs.foreach {
        case (f, ec) =>
          ec(f(computed))
      }
    }

    def onComplete[U](f: Try[T] => U)(implicit executor: ExecutionContext): Unit = this.synchronized {
      if (computed == null && listeners.isEmpty) {
        // Future is not computed and computation
        // process is not started. Lets add the listener
        // and start computation
        listeners = (f, executor) :: listeners
        executor(compute())
      } else if (computed != null) {
        // Future already computed. Lets invoke
        // the listener.
        executor(f(computed))
      } else {
        // Future is not computed but computation
        // is started. Lets add the listener.
        listeners = (f, executor) :: listeners
      }
    }

    def isCompleted: Boolean =
      computed != null

    def ready(atMost: Duration)(implicit permit: CanAwait): this.type = {
      val _ = scala.concurrent.Future.unit
        .flatMap(_ => this)(RunNowExecutionContext)
        .ready(atMost)
      this
    }

    def result(atMost: Duration)(implicit permit: CanAwait): T =
      scala.concurrent.Future.unit
        .flatMap(_ => this)(RunNowExecutionContext)
        .result(atMost)
  }

  final case class Async[T](k: (Either[Throwable, T] => Unit) => Unit) extends LazyLike[T] {
    protected def compute()(implicit executor: ExecutionContext): Unit =
      executor(k(result => complete(result.toTry)))
  }

  final case class Map[A, B](source: Future[A],
                             f: Try[A] => Try[B],
                             shift: ExecutionContext) extends LazyLike[B] {

    protected def compute()(implicit executor: ExecutionContext): Unit =
      source.onComplete { `try` =>
        complete(f(`try`))
      }(shift)
  }

  final case class Bind[A, B](source: Future[A],
                              f: Try[A] => Future[B],
                              shift: ExecutionContext) extends LazyLike[B] {

    protected def compute()(implicit executor: ExecutionContext): Unit =
      source.onComplete { `try` =>
        try {
          f(`try`) match {
            case Pure(pureValue) => complete(Success(pureValue))
            case Delay(thunk) => complete(Try(thunk()))
            case Error(error) => complete(Failure(error))
            case Never => () // Do nothing
            case future => future.onComplete(complete)
          }
        } catch {
          case error: Throwable =>
            complete(Failure(error))
        }
      }(shift)
  }

  /**
    * This future computes value (effect) every time
    * onComplete is invoked.
    */
  final case class Delay[+T](thunk: () => T) extends BrightFuture[T] {
    def value: Option[Try[T]] = Some(Try(thunk()))
    val isCompleted: Boolean = true
    def onComplete[U](f: Try[T] => U)(implicit executor: ExecutionContext): Unit =
      executor(f(Try(thunk())))
    def ready(atMost: Duration)(implicit permit: CanAwait): this.type = this
    def result(atMost: Duration)(implicit permit: CanAwait): T = thunk()
  }

  final case class Error(error: Throwable) extends BrightFuture[Nothing] {
    private lazy val tryPureValue = Failure(error)
    lazy val value: Option[Try[Nothing]] = Some(Failure(error))
    def isCompleted: Boolean = true
    def onComplete[U](f: Try[Nothing] => U)(implicit executor: ExecutionContext): Unit =
      executor(f(tryPureValue))
    override def transform[S](f: Try[Nothing] => Try[S])(implicit executor: ExecutionContext): Future[S] = this
    override def transformWith[S](f: Try[Nothing] => Future[S])(implicit executor: ExecutionContext): Future[S] = this
    def ready(atMost: Duration)(implicit permit: CanAwait): this.type = this
    def result(atMost: Duration)(implicit permit: CanAwait): Nothing = throw error
  }

  final case class Pure[+T](pureValue: T) extends BrightFuture[T] {
    private lazy val tryPureValue: Try[T] = Success(pureValue)
    lazy val value: Option[Try[T]] = Some(tryPureValue)
    def onComplete[U](f: Try[T] => U)(implicit executor: ExecutionContext): Unit =
      executor(f(tryPureValue))
    def isCompleted: Boolean = true
    override def ready(atMost: Duration)(implicit permit: CanAwait): this.type = this
    override def result(atMost: Duration)(implicit permit: CanAwait): T = pureValue
  }

  case object Never extends BrightFuture[Nothing] {
    def onComplete[U](f: Try[Nothing] => U)(implicit executor: ExecutionContext): Unit = ()
    def isCompleted: Boolean = false
    val value: Option[Try[Nothing]] = None
    override def transform[S](f: Try[Nothing] => Try[S])(implicit executor: ExecutionContext): Future[S] = this
    override def transformWith[S](f: Try[Nothing] => Future[S])(implicit executor: ExecutionContext): Future[S] = this
    override def ready(atMost: Duration)(implicit permit: CanAwait): Never.this.type =
      throw new TimeoutException("The bright future will never come")
    override def result(atMost: Duration)(implicit permit: CanAwait): Nothing =
      throw new TimeoutException("The bright future will never come")
  }

  final val unit = Pure(())

  private implicit class ExecutionContextOps(val ec: ExecutionContext) extends AnyVal {
    def apply[U](f: => U): Unit =
      if (ec == RunNowExecutionContext) f
      else ec.execute(() => f)
  }
}
