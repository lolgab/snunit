package snunit

import scala.scalanative.loop._

object EventPollingExecutorSchedulerImpl {
  def monitor(
      fd: Int,
      reads: Boolean,
      writes: Boolean,
      cb: EventPollingExecutorScheduler.EventNotificationCallback
  ): Runnable = {
    val poll = Poll(fd)
    poll.start(in = reads, out = writes)((rwResult: RWResult) =>
      cb.notifyEvents(readReady = rwResult.readable, writeReady = rwResult.writable)
    )
    () => poll.stop()
  }
}
