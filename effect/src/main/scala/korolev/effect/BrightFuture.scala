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

  def attempt: BrightFuture[Either[Throwable, T]] = Map(
    this, {
      case Success(value) => Success(Right(value))
      case Failure(value) => Success(Left(value))
    }: Try[T] => Try[Either[Throwable, T]], RunNowExecutionContext
  )

  /**
    * Same as [[onComplete]] but doesn't state
    * change state of computing BrightFutures
    * during computation.
    */
  def run[U](executor: ExecutionContext)(f: Try[T] => U): Unit
}

object BrightFuture {

  sealed trait OnComplete[T] extends BrightFuture[T] {

    @volatile private var computed: Try[T] = _
    @volatile private var listeners = List.empty[(Try[T] => _, ExecutionContext)]

    def value: Option[Try[T]] = this.synchronized {
      if (computed == null && listeners.isEmpty) {
        // Force computation
        this.onComplete(_ => ())(RunNowExecutionContext)
      }
      Option(computed)
    }

    def onComplete[U](f: Try[T] => U)(implicit executor: ExecutionContext): Unit = this.synchronized {
      if (computed == null && listeners.isEmpty) {
        // Future is not computed and computation
        // process is not started. Lets add the listener
        // and start computation
        listeners = (f, executor) :: listeners
        run(executor) { `try` =>
          // Update the state
          val xs = this.synchronized {
            val res = listeners
            listeners = Nil
            computed = `try`
            res
          }
          // Notify computed
          xs.foreach {
            case (f, ec) =>
              ec(f(computed))
          }
        }
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

  sealed trait LazyLike[T] extends BrightFuture[T] {

    protected def compute[U](complete: Try[T] => U): Unit

    def run[U](executor: ExecutionContext)(f: Try[T] => U): Unit = {
      // Execute in stateless mode
      executor(compute(f))
    }
  }

  final case class Async[T](k: (Either[Throwable, T] => Unit) => Unit) extends LazyLike[T] with OnComplete[T] {
    protected def compute[U](complete: Try[T] => U): Unit =
      k(result => complete(result.toTry))
  }

  final case class Map[A, B](source: Future[A],
                             f: Try[A] => Try[B],
                             shift: ExecutionContext) extends LazyLike[B] with OnComplete[B] {

    protected def compute[U](complete: Try[B] => U): Unit = {
      def runCallback(`try`: Try[A]): Unit = shift(complete(f(`try`)))
      source match {
        case bright: BrightFuture[A] => bright.run(shift)(runCallback)
        case standard: Future[A] => standard.onComplete(runCallback)(shift)
      }
    }
  }

  final case class Bind[A, B](source: Future[A],
                              f: Try[A] => Future[B],
                              shift: ExecutionContext) extends LazyLike[B] with OnComplete[B] {

    protected def compute[U](complete: Try[B] => U): Unit = {
      def runCallback(`try`: Try[A]): Unit = shift {
        try {
          f(`try`) match {
            case Never => () // Do nothing
            case Pure(pureValue) => complete(Success(pureValue))
            case Error(error) => complete(Failure(error))
            case Delay(thunk) => complete(Try(thunk()))
            case future => future.onComplete(complete)(shift)
          }
        } catch {
          case error: Throwable =>
            complete(Failure(error))
        }
      }
      source match {
        case bright: BrightFuture[A] => bright.run(shift)(runCallback)
        case standard: Future[A] => standard.onComplete(runCallback)(shift)
      }
    }
  }

  /**
    * This future computes value (effect) every time
    * run(stateless = true) is invoked.
    */
  final case class Delay[T](thunk: () => T) extends LazyLike[T] with OnComplete[T] {
    protected def compute[U](f: Try[T] => U): Unit =
      f(Try(thunk()))
  }

  final case class Error(error: Throwable) extends BrightFuture[Nothing] {
    private lazy val tryPureValue = Failure(error)
    lazy val value: Option[Try[Nothing]] = Some(Failure(error))
    def isCompleted: Boolean = true
    def run[U](executor: ExecutionContext)(f: Try[Nothing] => U): Unit =
      executor(f(tryPureValue))
    override def transform[S](f: Try[Nothing] => Try[S])(implicit executor: ExecutionContext): Future[S] = this
    override def transformWith[S](f: Try[Nothing] => Future[S])(implicit executor: ExecutionContext): Future[S] = this
    def ready(atMost: Duration)(implicit permit: CanAwait): this.type = this
    def result(atMost: Duration)(implicit permit: CanAwait): Nothing = throw error
    def onComplete[U](f: Try[Nothing] => U)(implicit executor: ExecutionContext): Unit =
      run(executor)(f)
  }

  final case class Pure[+T](pureValue: T) extends BrightFuture[T] {
    private lazy val tryPureValue: Try[T] = Success(pureValue)
    lazy val value: Option[Try[T]] = Some(tryPureValue)
    def run[U](executor: ExecutionContext)(f: Try[T] => U): Unit =
      executor(f(tryPureValue))
    def isCompleted: Boolean = true
    def ready(atMost: Duration)(implicit permit: CanAwait): this.type = this
    def result(atMost: Duration)(implicit permit: CanAwait): T = pureValue
    def onComplete[U](f: Try[T] => U)(implicit executor: ExecutionContext): Unit =
      run(executor)(f)
  }

  case object Never extends BrightFuture[Nothing] {
    def isCompleted: Boolean = false
    val value: Option[Try[Nothing]] = None
    override def transform[S](f: Try[Nothing] => Try[S])(implicit executor: ExecutionContext): Future[S] = this
    override def transformWith[S](f: Try[Nothing] => Future[S])(implicit executor: ExecutionContext): Future[S] = this
    def ready(atMost: Duration)(implicit permit: CanAwait): Never.this.type =
      throw new TimeoutException("The bright future will never come")
    def result(atMost: Duration)(implicit permit: CanAwait): Nothing =
      throw new TimeoutException("The bright future will never come")
    def run[U](executor: ExecutionContext)(f: Try[Nothing] => U): Unit = ()
    def onComplete[U](f: Try[Nothing] => U)(implicit executor: ExecutionContext): Unit = ()
  }

  final val unit = Pure(())

  private implicit class ExecutionContextOps(val ec: ExecutionContext) extends AnyVal {
    def apply[U](f: => U): Unit =
      if (ec == RunNowExecutionContext) f
      else ec.execute(() => f)
  }
}
