package epollcat.snunit

import epollcat.unsafe.EpollRuntime

object EventPollingExecutorScheduler {
  private val scheduler = EpollRuntime.global.compute.asInstanceOf[epollcat.unsafe.EventPollingExecutorScheduler]

  // This initializes the EpollRuntime and schedules it to the
  // global Scala Native ExecutionContext
  scheduler.execute(() => ())

  def monitorReads(fd: Int, cb: Runnable): Runnable = {
    scheduler.monitor(fd = fd, reads = true, writes = false) { (readReady: Boolean, writeReady: Boolean) =>
      cb.run()
    }
  }
}
