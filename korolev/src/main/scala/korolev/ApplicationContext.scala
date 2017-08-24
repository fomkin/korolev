package korolev

import korolev.execution.Scheduler
import levsha.Document
import levsha.events.EventPhase
import Async._

import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

/**
  * Legacy (version < 0.6.0) API layer.
  * @see [[Context]]
  * @deprecated
  */
final class ApplicationContext[F[+ _]: Async: Scheduler, S, M] {

  import Context._
  import ApplicationContext._
  import EventPhase._

  type Effect = Context.Effect[F, S, M]
  type Event = Context.Event[F, S, M]
  type EventFactory[T] = T => Event
  type Transition = korolev.Transition[S]
  type Render = PartialFunction[S, Document.Node[Effect]]
  type ElementId = Context.ElementId[F, S, M]
  type Access = ApplicationContext.LegacyAccess[F, S, M]

  val symbolDsl = new KorolevTemplateDsl[F, S, M]()
  val modern = new Context[F, S, M]()

  def elementId = new Context.ElementId[F, S, M]()

  /**
    * Schedules the [[transition]] with [[delay]]. For example it can be useful
    * when you want to hide something after timeout.
    */
  def delay(delay: FiniteDuration)(transition: Transition): Delay[F, S, M] = new Delay[F, S, M] {

    @volatile private var handler = Option.empty[Scheduler.JobHandler[F, _]]
    @volatile private var finished = false

    def isFinished: Boolean = finished

    def cancel(): Unit = {
      handler.foreach(_.cancel())
    }

    def start(access: Context.Access[F, S, M]): Unit = {
      handler = Some {
        Scheduler[F].scheduleOnce(delay) {
          access.transition(transition).runIgnoreResult
        }
      }
    }
  }

  private def provideEventResult(ler: LegacyEventResult[F, S], access: Context.Access[F, S, M]) = {
    val effect = {
      for {
        _ <- ler.immediate.fold(Async[F].unit)(access.transition)
        _ <- ler.deferred.map(_.flatMap(access.transition)).getOrElse(Async[F].unit)
      } yield ()
    }
    EventResult[F, S](effect, ler.shouldStopPropagation)
  }

  def eventWithAccess(name: Symbol, phase: EventPhase = Bubbling)(f: Access => LegacyEventResult[F, S]): Event = {
    Event(name, phase, access => provideEventResult(f(new LegacyAccess[F, S, M](access)), access))
  }

  def event(name: Symbol, phase: EventPhase = Bubbling)(ler: => LegacyEventResult[F, S]): Event = {
    Event(name, phase, access => provideEventResult(ler, access))
  }

  def immediateTransition(transition: Transition): LegacyEventResult[F, S] =
    LegacyEventResult(immediate = Some(transition))

  def deferredTransition(transition: F[Transition]): LegacyEventResult[F, S] =
    LegacyEventResult(deferred = Some(transition))

  /**
    * This is an immediateTransition return same state
    */
  def noTransition: LegacyEventResult[F, S] = immediateTransition {
    case anyState => anyState
  }

  val emptyTransition: PartialFunction[S, S] = { case x => x }

  def transition(t: Transition): Transition = t
}

object ApplicationContext {

  @deprecated("This is compatibility layer for old fashioned API. Use Context instead.", "0.6.0")
  def apply[F[+_]: Async, S, M](implicit scheduler: Scheduler[F]) =
    new ApplicationContext[F, S, M]()

  case class LegacyEventResult[F[+ _]: Async, S](
      immediate: Option[Transition[S]] = None,
      deferred: Option[F[Transition[S]]] = None,
      shouldStopPropagation: Boolean = false
  ) {
    def immediateTransition(t: Transition[S]): LegacyEventResult[F, S] =
      copy(immediate = Some(t))

    def deferredTransition(t: F[Transition[S]]): LegacyEventResult[F, S] =
      copy(deferred = Some(t))

    def stopPropagation: LegacyEventResult[F, S] =
      copy(shouldStopPropagation = true)
  }

  import Context._

  class LegacyPropertyHandler[F[+_]: Async, T: ClassTag](propertyHandler: PropertyHandler[F]) {

    import reflect.classTag

    def get(propName: Symbol): F[T] = propertyHandler.get(propName) map { value =>
      val valueToCast = classTag[T] match {
        case t if t == classTag[String] => value
        case t if t == classTag[Int] => value.toInt
        case t if t == classTag[Double] => value.toDouble
        case t if t == classTag[Long] => value.toLong
        case t if t == classTag[Float] => value.toFloat
        case t if t == classTag[Boolean] => value.toBoolean
      }
      valueToCast.asInstanceOf[T]
    }

    def set(propName: Symbol, value: T): F[Unit] = {
      propertyHandler.set(propName, value)
    }
  }

  final class LegacyAccess[F[+_]: Async, S, M](access: Access[F, S, M]) {

    def property[T: ClassTag](id: ElementId[F, S, M]): LegacyPropertyHandler[F, T] =
      new LegacyPropertyHandler(access.property(id))

    def property[T: ClassTag](id: ElementId[F, S, M], propName: Symbol): F[T] =
      property[T](id).get(propName)

    def focus(id: ElementId[F, S, M]): F[Unit] =
      access.focus(id)

    def publish(message: M): F[Unit] =
      access.publish(message)

    def downloadFormData(id: ElementId[F, S, M]): FormDataDownloader[F, S] =
      access.downloadFormData(id)
  }
}
