package snunit

import scala.concurrent.duration._

private[snunit] object EventPollingExecutorScheduler {
  def monitorReads(
      fd: Int,
      cb: Runnable
  ): Runnable = {
    epollcat.snunit.InternalEventPollingExecutorSchedulerImpl.monitorReads(fd, cb)
  }

  def execute(runnable: Runnable): Runnable = {
    epollcat.unsafe.EpollRuntime.global.scheduler.sleep(FiniteDuration(0, SECONDS), runnable)
  }
}
