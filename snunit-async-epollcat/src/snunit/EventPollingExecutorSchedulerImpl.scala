package snunit

object EventPollingExecutorSchedulerImpl {
  def monitor(
      fd: Int,
      reads: Boolean,
      writes: Boolean,
      cb: EventPollingExecutorScheduler.EventNotificationCallback
  ): Runnable = {
    epollcat.snunit.EventPollingExecutorScheduler.monitor(fd = fd, reads = reads, writes = writes)(cb)
  }
}
