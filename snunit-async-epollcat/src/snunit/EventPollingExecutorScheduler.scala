package snunit

private[snunit] object EventPollingExecutorScheduler {
  def monitorReads(
      fd: Int,
      cb: Runnable
  ): Runnable = {
    epollcat.snunit.InternalEventPollingExecutorSchedulerImpl.monitorReads(fd, cb)
  }

  def execute(runnable: Runnable): Runnable = {
    implicit val runtime = epollcat.unsafe.EpollRuntime.global
    val callback = cats.effect.IO(runnable.run()).unsafeRunCancelable()
    () => callback.apply()
  }
}
