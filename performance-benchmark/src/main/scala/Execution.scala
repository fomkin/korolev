import java.nio.channels.AsynchronousChannelGroup
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

/**
  * @author Aleksey Fomkin <aleksey.fomkin@gmail.com>
  */
object Execution {

  final val ScheduledThreadPool = Executors.newScheduledThreadPool(16)

  implicit val defaultExecutor = ExecutionContext.fromExecutorService(ScheduledThreadPool)
  implicit val channelGroup = AsynchronousChannelGroup.withThreadPool(defaultExecutor)
  implicit val strategy = fs2.Strategy.fromExecutionContext(defaultExecutor)
  implicit val scheduler = fs2.Scheduler.fromScheduledExecutorService(ScheduledThreadPool)
  implicit val stringCodec = scodec.codecs.string(StandardCharsets.UTF_8)
}
