package epollcat.unsafe

import epollcat.unsafe.EpollRuntime

object InternalEventPollingExecutorSchedulerImpl {
  private val scheduler = EpollRuntime.global.compute.asInstanceOf[epollcat.unsafe.EventPollingExecutorScheduler]

  // This initializes the EpollRuntime and schedules it to the
  // global Scala Native ExecutionContext
  scheduler.execute(() => ())

  def monitorReads(fd: Int, cb: Runnable): Runnable = {
    val newCb = new epollcat.unsafe.EventNotificationCallback {
      def notifyEvents(reads: Boolean, writes: Boolean): Unit = {
        cb.run()
      }
    }
    scheduler.monitor(fd = fd, reads = true, writes = false)(newCb)
  }
}
