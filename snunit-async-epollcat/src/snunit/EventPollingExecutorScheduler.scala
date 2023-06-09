package snunit

private[snunit] object EventPollingExecutorScheduler {
  def monitorReads(
      fd: Int,
      cb: Runnable
  ): Runnable = {
    epollcat.unsafe.InternalEventPollingExecutorSchedulerImpl.monitorReads(fd, cb)
  }

  def execute(runnable: Runnable): Unit = {
    epollcat.unsafe.EpollRuntime.global.compute.execute(runnable)
  }

  def shutdown(): Unit = {
    epollcat.unsafe.EpollRuntime.global.shutdown()
  }
}
