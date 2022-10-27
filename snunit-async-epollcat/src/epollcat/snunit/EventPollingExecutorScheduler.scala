package epollcat.snunit

import epollcat.unsafe.EpollRuntime

object EventPollingExecutorScheduler {
  private val scheduler = EpollRuntime.global.compute.asInstanceOf[epollcat.unsafe.EventPollingExecutorScheduler]

  def monitor(fd: Int, reads: Boolean, writes: Boolean)(
      cb: snunit.EventPollingExecutorScheduler.EventNotificationCallback
  ): Runnable = {
    scheduler.monitor(fd = fd, reads = reads, writes = writes) { (readReady: Boolean, writeReady: Boolean) =>
      cb.notifyEvents(readReady = readReady, writeReady = writeReady)
    }
  }
}
