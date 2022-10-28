package snunit

import scala.concurrent.duration._

private [snunit] object EventPollingExecutorScheduler {
  def monitorReads(
      fd: Int,
      cb: Runnable
  ): Runnable = {
    epollcat.snunit.EventPollingExecutorScheduler.monitorReads(fd, cb)
  }

  private val zero = FiniteDuration(0, SECONDS)
  def execute(runnable: Runnable): Runnable = {
    epollcat.unsafe.EpollRuntime.global.scheduler.sleep(zero, runnable)
  }
}
