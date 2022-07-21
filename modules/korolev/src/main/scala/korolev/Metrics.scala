package korolev

import java.util.concurrent.atomic.AtomicLong

object Metrics {

  sealed trait Metric[T]

  final class LongMetric(name: String) extends Metric[Long] {
    private val ref = new AtomicLong(0)
    def update(f: Long => Long): Unit = ref.updateAndGet(x => f(x))
    def get: Long = ref.get()
  }

  // Diff time includes changes inference and preparation of the message for a browser.
  final val MinDiffNanos = new LongMetric("min-diff-nanos")
  final val MaxDiffNanos = new LongMetric("max-diff-nanos")
}