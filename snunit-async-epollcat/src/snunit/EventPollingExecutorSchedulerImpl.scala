package snunit

object EventPollingExecutorSchedulerImpl {
  import epollcat.snunit.EventPollingExecutorScheduler._
  def monitor(
      fd: Int,
      reads: Boolean,
      writes: Boolean,
      cb: EventPollingExecutorScheduler.EventNotificationCallback
  ): Runnable = {
    epollcat.snunit.EventPollingExecutorScheduler.monitor(fd = fd, reads = reads, writes = writes)(cb)
  }
}
